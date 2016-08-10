/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */

package org.openmrs.mobile.api;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;

import com.activeandroid.query.Select;

import org.openmrs.mobile.R;
import org.openmrs.mobile.activities.PatientListActivity;
import org.openmrs.mobile.application.OpenMRS;
import org.openmrs.mobile.dao.EncounterDAO;
import org.openmrs.mobile.dao.PatientDAO;
import org.openmrs.mobile.dao.VisitDAO;
import org.openmrs.mobile.models.Visit;
import org.openmrs.mobile.models.retrofit.Encounter;
import org.openmrs.mobile.models.retrofit.Encountercreate;
import org.openmrs.mobile.models.retrofit.FormResource;
import org.openmrs.mobile.net.VisitsManager;
import org.openmrs.mobile.net.helpers.VisitsHelper;
import org.openmrs.mobile.utilities.NetworkUtils;
import org.openmrs.mobile.utilities.ToastUtil;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EncounterService extends IntentService {
    final RestApi apiService =
            RestServiceBuilder.createService(RestApi.class);

    public EncounterService() {
        super("Save Encounter");
    }

    public void addEncounter(final Encountercreate encountercreate) {

        encountercreate.save();

        if(NetworkUtils.isNetworkAvailable()) {

            if (new VisitDAO().isPatientNowOnVisit(encountercreate.getPatientId())) {
                Visit visit = new VisitDAO().getPatientCurrentVisit(encountercreate.getPatientId());
                encountercreate.setVisit(visit.getUuid());
                syncEncounter(encountercreate);

            } else {
                new VisitsManager().checkVisitBeforeStart(
                        VisitsHelper.createCheckVisitsBeforeStartListener(encountercreate.getPatientId(), encountercreate, this));
            }
        }
        else
            ToastUtil.error("No internet connection. Form data is saved locally " +
                    "and will sync when internet connection is restored. ");
    }

    public void syncEncounter(final Encountercreate encountercreate) {

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(OpenMRS.getInstance());
        Boolean syncstate = prefs.getBoolean("sync", true);

        if (syncstate) {

            encountercreate.pullObslist();
            Call<Encounter> call = apiService.createEncounter(encountercreate);
            call.enqueue(new Callback<Encounter>() {
                @Override
                public void onResponse(Call<Encounter> call, Response<Encounter> response) {
                    if (response.isSuccessful()) {
                        Encounter encounter = response.body();
                        linkvisit(encountercreate.getPatientId(),encountercreate.getFormname(), encounter);
                        encountercreate.setSynced(true);
                        encountercreate.save();
                    } else {
                        //ToastUtil.error("Could not save encounter");
                    }
                }

                @Override
                public void onFailure(Call<Encounter> call, Throwable t) {
                    ToastUtil.error(t.getLocalizedMessage());

                }
            });

        } else {
            ToastUtil.error("Sync is off. Turn on sync to save form data.");
        }

    }


    void linkvisit(Long patientid, String formname, Encounter encounter)
    {
        Long visitid=new VisitDAO().getVisitsIDByUUID(encounter.getVisit().getUuid());
        Visit visit=new VisitDAO().getVisitsByID(visitid);
        encounter.setEncounterTypeToken(Encounter.EncounterTypeToken.getType(formname));
        List<Encounter> encounterList=visit.getEncounters();
        encounterList.add(encounter);
        new VisitDAO().updateVisit(visit,visit.getId(),patientid);
        ToastUtil.success(formname+" data saved successfully");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if(NetworkUtils.isNetworkAvailable()) {

            List<Encountercreate> encountercreatelist = new Select()
                    .from(Encountercreate.class)
                    .execute();

            for(Encountercreate encountercreate:encountercreatelist)
            {
                if(encountercreate.getSynced()==false)
                {
                    if (new VisitDAO().isPatientNowOnVisit(encountercreate.getPatientId())) {
                        Visit visit = new VisitDAO().getPatientCurrentVisit(encountercreate.getPatientId());
                        encountercreate.setVisit(visit.getUuid());
                        syncEncounter(encountercreate);

                    } else {
                        new VisitsManager().checkVisitBeforeStart(
                                VisitsHelper.createCheckVisitsBeforeStartListener(encountercreate.getPatientId(), encountercreate, this));
                    }
                }
            }


        } else {
            ToastUtil.error("No internet connection. Form data is saved locally " +
                    "and will sync when internet connection is restored. ");
        }
    }

}