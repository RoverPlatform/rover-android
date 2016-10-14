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

    public static Customer getInstance(Context context) {

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
        mTraits = new HashMap<>();
    }

    public Optional<String> getIdentifier() { return mIdentifier; }
    public Optional<String> getFirstName() { return mFirstName; }
    public Optional<String> getLastName() { return mLastName; }
    public Optional<String> getGender() { return mGender; }
    public Optional<Integer> getAge() { return mAge; }
    public Optional<String> getEmail() { return mEmail; }
    public Optional<String> getPhoneNumber() { return mPhoneNumber; }
    public Optional<String[]> getTags() { return mTags; }
    //public Map<String, Object> getTraits() { return mTraits; }

    public void setIdentifier(String identifier) { mIdentifier.set(identifier); }
    public void setFirstName(String name) { mFirstName .set(name); }
    public void setLastName(String name) { mLastName.set(name); }
    public void setGender(String gender) { mGender.set(gender); }
    public void setAge(Integer age) { mAge.set(age); }
    public void setEmail(String email) { mEmail.set(email); }
    public void setPhoneNumber(String phoneNumber) { mPhoneNumber.set(phoneNumber); }
    public void setTags(String[] tags) { mTags.set(tags); }

    public void clearIdentifier() { mIdentifier.clear(); }
    public void clearFirstName() { mFirstName.clear(); }
    public void clearLastName() { mLastName.clear(); }
    public void clearGender() { mGender.clear(); }
    public void clearAge() { mAge.clear(); }
    public void clearEmail() { mEmail.clear(); }
    public void clearPhoneNumber() { mPhoneNumber.clear(); }
    public void clearTags() { mTags.clear(); }

    public void clear() {
        clearIdentifier();
        clearFirstName();
        clearLastName();
        clearGender();
        clearAge();
        clearEmail();
        clearPhoneNumber();
        clearTags();
    }

    public void save(Context context) {
        SharedPreferences.Editor editor = context.getSharedPreferences(SHARED_CUSTOMER, 0).edit();
        editor.clear();

        if (mIdentifier.hasBeenSet()) {
            editor.putString("identifier", mIdentifier.get());
        }

        if (mFirstName.hasBeenSet()) {
            editor.putString("first-name", mFirstName.get());
        }

        if (mLastName.hasBeenSet()) {
            editor.putString("last-name", mLastName.get());
        }

        if (mGender.hasBeenSet()) {
            editor.putString("gender", mGender.get());
        }

        if (mAge.hasBeenSet()) {
            editor.putInt("age", mAge.get());
        }

        if (mEmail.hasBeenSet()) {
            editor.putString("email", mEmail.get());
        }

        if (mPhoneNumber.hasBeenSet()) {
            editor.putString("phoneNumber", mPhoneNumber.get());
        }

        if (mTags.hasBeenSet()) {
            Set<String> tagsSet = new HashSet<>(Arrays.asList((String[])mTags.getOrElse(new String [0])));
            editor.putStringSet("tags", tagsSet);
        }

        if (mTraits != null) {
            JSONObject jsonObject = new JSONObject();

            Iterator<String> keys = mTraits.keySet().iterator();
            while (keys.hasNext()) {
                String key = keys.next();

                try {
                    Object value = mTraits.get(key);
                    if (value instanceof Double) {
                        jsonObject.put(key, ((Double) value).doubleValue());
                    } else if (value instanceof String) {
                        jsonObject.put(key, value);
                    }
                } catch (JSONException e) {
                    Log.e("Customer", "Error storing traits");
                }
            }

            editor.putString("traits", jsonObject.toString());
        }
        editor.apply();
    }

    private static Customer pullSharedCustomerFromSharedPrefs(Context context) {
        Customer customer = new Customer();

        SharedPreferences sharedData = context.getSharedPreferences(SHARED_CUSTOMER, 0);
        if (sharedData.contains("identifier")) {
            customer.setIdentifier(sharedData.getString("identifier", null));
        }

        if (sharedData.contains("first-name")) {
            customer.setFirstName(sharedData.getString("first-name", null));
        }

        if (sharedData.contains("last-name")) {
            customer.setLastName(sharedData.getString("last-name", null));
        }

        if (sharedData.contains("gender")) {
            customer.setGender(sharedData.getString("gender", null));
        }

        if (sharedData.contains("age")) {
            customer.setAge(sharedData.getInt("age", 0));
        }

        if (sharedData.contains("email")) {
            customer.setEmail(sharedData.getString("email", null));
        }

        if (sharedData.contains("phoneNumber")) {
            customer.setPhoneNumber(sharedData.getString("phoneNumber", null));
        }

        if (sharedData.contains("tags")) {
            Set<String> tagsSet = sharedData.getStringSet("tags", null);
            if (tagsSet != null) {
                customer.setTags(tagsSet.toArray(new String[tagsSet.size()]));
            }
        }

        String traitsString = sharedData.getString("traits", null);
        if (traitsString != null) {

            JSONObject jsonObject = null;

            try {
                jsonObject = new JSONObject(traitsString);
            } catch (JSONException e) {
                Log.e("Customer", "Invalid traits");
            }

            if (jsonObject != null) {
                Iterator<String> keys = jsonObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();

                    try {
                        double number = jsonObject.getDouble(key);

                        customer.mTraits.put(key, new Double(number));
                    } catch (JSONException e) {
                        String string = jsonObject.optString(key, "");

                        customer.mTraits.put(key, string);
                    }
                }
            }
        }

        return customer;
    }
}
