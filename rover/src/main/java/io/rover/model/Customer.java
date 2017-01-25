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

    private Optional<String> mIdentifier;
    private Optional<String> mFirstName;
    private Optional<String> mLastName;
    private Optional<String> mGender;
    private Optional<Integer> mAge;
    private Optional<String> mEmail;
    private Optional<String> mPhoneNumber;
    private Optional<String[]> mTags;
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
        mIdentifier = new Optional<>();
        mFirstName = new Optional<>();
        mLastName = new Optional<>();
        mGender = new Optional<>();
        mAge = new Optional<>();
        mEmail = new Optional<>();
        mPhoneNumber = new Optional<>();
        mTags = new Optional<>();
        mTraits = null;
    }

    public Optional<String> getIdentifier() { return mIdentifier; }
    public Optional<String> getFirstName() { return mFirstName; }
    public Optional<String> getLastName() { return mLastName; }
    public Optional<String> getGender() { return mGender; }
    public Optional<Integer> getAge() { return mAge; }
    public Optional<String> getEmail() { return mEmail; }
    public Optional<String> getPhoneNumber() { return mPhoneNumber; }
    public Optional<String[]> getTags() { return mTags; }
    public Map<String, Object> getTraits() { return mTraits; }

    public void setIdentifier(String identifier) { mIdentifier.set(identifier); }
    public void setFirstName(String name) { mFirstName.set(name); }
    public void setLastName(String name) { mLastName.set(name); }
    public void setGender(String gender) { mGender.set(gender); }
    public void setAge(int age) { mAge.set(age); }
    public void setEmail(String email) { mEmail.set(email); }
    public void setPhoneNumber(String phoneNumber) { mPhoneNumber.set(phoneNumber); }
    public void setTags(String[] tags) { mTags.set(tags); }
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

    public void clearIdentifier() { mIdentifier.set(null); }
    public void clearFirstName() { mFirstName.set(null); }
    public void clearLastName() { mLastName.set(null); }
    public void clearGender() { mGender.set(null); }
    public void clearAge() { mAge.set(null); }
    public void clearEmail() { mEmail.set(null); }
    public void clearPhoneNumber() { mPhoneNumber.set(null); }
    public void clearTags() { mTags.set(new String[0]); }
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
            editor.commit();
            editor.apply();
        }
    }

    public void save(Context context) {
        synchronized (this) {
            SharedPreferences.Editor editor = context.getSharedPreferences(SHARED_CUSTOMER, 0).edit();
            editor.clear();

            if (mIdentifier.hasBeenSet())
                editor.putString("identifier", mIdentifier.get());

            if (mFirstName.hasBeenSet())
                editor.putString("first-name", mFirstName.get());

            if (mLastName.hasBeenSet())
                editor.putString("last-name", mLastName.get());

            if (mGender.hasBeenSet())
                editor.putString("gender", mGender.get());

            if (mAge.hasBeenSet() && mAge.get() != null)
                editor.putInt("age", mAge.get());

            if (mEmail.hasBeenSet())
                editor.putString("email", mEmail.get());

            if (mPhoneNumber.hasBeenSet())
                editor.putString("phoneNumber", mPhoneNumber.get());

            if (mTags.hasBeenSet()) {
                Set<String> tagsSet = new HashSet<>(Arrays.asList(mTags.getOrElse(new String [0])));
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
