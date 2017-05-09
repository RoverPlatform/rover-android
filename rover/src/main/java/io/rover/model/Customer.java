package io.rover.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import io.rover.util.Optional;

/**
 * Created by ata_n on 2016-03-24.
 */
public class Customer {
    private static final String TAG = "RoverCustomer";

    private String mIdentifier;
    private String mFirstName;
    private String mLastName;
    private String mGender;
    private Integer mAge;
    private String mEmail;
    private String mPhoneNumber;
    private String[] mTags;
    private Map<String, Object> mTraits;

    private static Customer mSharedCustomer;
    private static String SHARED_CUSTOMER = "ROVER_SHARED_CUSTOMER";

    public static synchronized Customer getInstance(Context context) {

        if (mSharedCustomer != null) {
            return mSharedCustomer;
        }

        mSharedCustomer = pullSharedCustomerFromSharedPrefs(context);
        return mSharedCustomer;
    }

    private Customer() {
        mIdentifier = null;
        mFirstName = null;
        mLastName = null;
        mGender = null;
        mAge = null;
        mEmail = null;
        mPhoneNumber = null;
        mTags = new String[0];
        mTraits = null;
    }

    public String getIdentifier() { return mIdentifier; }
    public String getFirstName() { return mFirstName; }
    public String getLastName() { return mLastName; }
    public String getGender() { return mGender; }
    public Integer getAge() { return mAge; }
    public String getEmail() { return mEmail; }
    public String getPhoneNumber() { return mPhoneNumber; }
    public String[] getTags() { return mTags; }
    public Map<String, Object> getTraits() { return mTraits; }

    public void setIdentifier(String identifier) { mIdentifier = identifier; }
    public void setFirstName(String name) { mFirstName = name; }
    public void setLastName(String name) { mLastName = name; }
    public void setGender(String gender) { mGender = gender; }
    public void setAge(int age) { mAge = age; }
    public void setEmail(String email) { mEmail = email; }
    public void setPhoneNumber(String phoneNumber) { mPhoneNumber = phoneNumber; }
    public void setTags(String[] tags) {
        if (tags == null) {
            clearTags();
        } else {
            mTags = tags;
        }
    }
    public void setTraits(Map<String, Object> traits) {
        if (traits == null || traits.isEmpty())
            return;

        if (mTraits == null)
            mTraits = new HashMap<String, Object>();

        // Merge the new traits into the existing traits
        for (Map.Entry<String, Object> entry : traits.entrySet()) {
            mTraits.put(entry.getKey(), entry.getValue());
        }

        // Remove any key which was set to null

        for (Iterator<Map.Entry<String, Object>> it = mTraits.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, Object> entry = it.next();
            if (entry.getValue() == null)
                it.remove();
        }

        if (mTraits.isEmpty())
            clearTraits();
    }

    public void clearIdentifier() { mIdentifier = null; }
    public void clearFirstName() { mFirstName = null; }
    public void clearLastName() { mLastName = null; }
    public void clearGender() { mGender = null; }
    public void clearAge() { mAge = null; }
    public void clearEmail() { mEmail = null; }
    public void clearPhoneNumber() { mPhoneNumber = null; }
    public void clearTags() { mTags = new String[0]; }
    public void clearTraits() { mTraits = null; }

    public void clear(Context context) {
        synchronized (this) {

            clearIdentifier();
            clearFirstName();
            clearLastName();
            clearGender();
            clearAge();
            clearEmail();
            clearPhoneNumber();
            clearTags();
            clearTraits();

            SharedPreferences.Editor editor = context.getSharedPreferences(SHARED_CUSTOMER, 0).edit();
            editor.clear();
            editor.apply();
        }
    }

    public void save(Context context) {
        synchronized (this) {
            SharedPreferences.Editor editor = context.getSharedPreferences(SHARED_CUSTOMER, 0).edit();
            editor.clear();

            editor.putString("identifier", mIdentifier);
            editor.putString("first-name", mFirstName);
            editor.putString("last-name", mLastName);
            editor.putString("gender", mGender);
            editor.putString("email", mEmail);
            editor.putString("phoneNumber", mPhoneNumber);


            if (mAge != null)
            editor.putInt("age", mAge);



            if (mTags != null) {
                Set<String> tagsSet = new HashSet<>(Arrays.asList(mTags));
                editor.putStringSet("tags", tagsSet);
            }

            if (mTraits != null) {
                try {
                    JSONObject jsonObject = new JSONObject(mTraits);
                    editor.putString("traits", jsonObject.toString());
                } catch (NullPointerException e) {
                    Log.e("Customer", "Failed to save traits");
                }
            }

            editor.apply();
        }
    }

    private static Customer pullSharedCustomerFromSharedPrefs(Context context) {
        Customer customer = new Customer();

        if (context == null) {
            Log.w(TAG, "Context was null while trying to read custom from shared prefs");
            return customer;
        }

        SharedPreferences sharedData = context.getSharedPreferences(SHARED_CUSTOMER, 0);

        if (sharedData.contains("identifier"))
            customer.setIdentifier(sharedData.getString("identifier", null));

        if (sharedData.contains("first-name"))
            customer.setFirstName(sharedData.getString("first-name", null));

        if (sharedData.contains("last-name"))
            customer.setLastName(sharedData.getString("last-name", null));

        if (sharedData.contains("gender"))
            customer.setGender(sharedData.getString("gender", null));

        if (sharedData.contains("age"))
            customer.setAge(sharedData.getInt("age", 0));

        if (sharedData.contains("email"))
            customer.setEmail(sharedData.getString("email", null));

        if (sharedData.contains("phoneNumber"))
            customer.setPhoneNumber(sharedData.getString("phoneNumber", null));

        if (sharedData.contains("tags")) {
            Set<String> tagsSet = sharedData.getStringSet("tags", null);
            if (tagsSet != null) {
                customer.setTags(tagsSet.toArray(new String[tagsSet.size()]));
            }
        }

        if (sharedData.contains("traits")) {
            String traitsJSONString = sharedData.getString("traits", null);

            if (traitsJSONString != null) {
                JSONObject jsonObject = null;

                try {
                    jsonObject = new JSONObject(traitsJSONString);
                } catch (JSONException e) {
                    Log.e("Customer", "Invalid traits stored in shared prefs");
                }

                HashMap<String, Object> parsedTraits = new HashMap<>();

                if (jsonObject != null) {
                    Iterator<String> keys = jsonObject.keys();

                    while (keys.hasNext()) {
                        String key = keys.next();
                        try {
                            parsedTraits.put(key, jsonObject.get(key));
                        } catch (JSONException e) {
                            Log.e("Customer", "Failed to parse trait: " + key);
                        }
                    }

                    customer.setTraits(parsedTraits);
                }
            }
        }

        return customer;
    }
}
