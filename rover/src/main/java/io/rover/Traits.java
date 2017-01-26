package io.rover;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by chrisrecalis on 2016-11-01.
 */
public class Traits {

    private static final String IDENTIFIER_KEY = "identifier";
    private static final String FIRST_NAME_KEY = "first-name";
    private static final String LAST_NAME_KEY = "last-name";
    private static final String GENDER_KEY = "gender";
    private static final String AGE_KEY = "age";
    private static final String EMAIL_KEY = "email";
    private static final String PHONE_NUMBER_KEY = "phone-number";
    private static final String TAGS_KEY = "tags";
    private static final Set<String> DEFINED_TRAIT_KEYS = new HashSet<String>(Arrays.asList(IDENTIFIER_KEY, FIRST_NAME_KEY, LAST_NAME_KEY, GENDER_KEY, AGE_KEY, EMAIL_KEY, PHONE_NUMBER_KEY, TAGS_KEY));

    private HashMap<String, Object> mValueMap;

    public Traits() {
        mValueMap = new HashMap<>();
    }

    public Traits putIdentifier(String identifier) {
        return putValue(IDENTIFIER_KEY, identifier);
    }

    public String getIdentifier() {
        return getString(IDENTIFIER_KEY);
    }

    public boolean hasIdentifier() {
        return hasKey(IDENTIFIER_KEY);
    }

    public Traits putFirstName(String firstName) {
        return putValue(FIRST_NAME_KEY, firstName);
    }

    public String getFirstName() {
        return getString(FIRST_NAME_KEY);
    }

    public boolean hasFirstName() {
        return hasKey(FIRST_NAME_KEY);
    }

    public Traits putLastName(String lastName) {
        return putValue(LAST_NAME_KEY, lastName);
    }

    public String getLastName() {
        return getString(LAST_NAME_KEY);
    }

    public boolean hasLastName() {
        return hasKey(LAST_NAME_KEY);
    }

    public Traits putGender(String gender) {
        return putValue(GENDER_KEY, gender);
    }

    public String getGender() {
        return getString(GENDER_KEY);
    }

    public boolean hasGender() {
        return hasKey(GENDER_KEY);
    }

    public Traits putAge(int age) {
        return putValue(AGE_KEY, age);
    }

    public int getAge() {
        return getInt(AGE_KEY, 0);
    }

    public boolean hasAge() {
        return hasKey(AGE_KEY);
    }

    public Traits putEmail(String email) {
        return putValue(EMAIL_KEY, email);
    }

    public String getEmail() {
        return getString(EMAIL_KEY);
    }

    public boolean hasEmail() {
        return hasKey(EMAIL_KEY);
    }

    public Traits putPhoneNumber(String phoneNumber) {
        return putValue(PHONE_NUMBER_KEY, phoneNumber);
    }

    public String getPhoneNumber() {
        return getString(PHONE_NUMBER_KEY);
    }

    public boolean hasPhoneNumber() {
        return hasKey(PHONE_NUMBER_KEY);
    }

    public Traits putTags(Set<String> tags) {
        return putTags(tags.toArray(new String[tags.size()]));
    }

    public Traits putTags(String[] tags) {
        return putValue(TAGS_KEY, tags);
    }

    public String[] getTags() {
        return getStringArray(TAGS_KEY);
    }

    public boolean hasTags() {
        return hasKey(TAGS_KEY);
    }

    public HashMap<String, Object> getCustomTraits() {
        HashMap<String, Object> customTraits = new HashMap<>(mValueMap);
        customTraits.keySet().removeAll(DEFINED_TRAIT_KEYS);
        return customTraits;
    }

    public boolean hasCustomTraits() {
        return getCustomTraits().size() > 0;
    }

    public Traits putValue(String key, Object value) {
        mValueMap.put(key, value);
        return this;
    }

    private Object get(String key) {
        return mValueMap.get(key);
    }

    public boolean hasKey(String key) {
        return mValueMap.containsKey(key);
    }

    private String getString(String key) {
        Object value = get(key);
        if (value instanceof String) {
            return (String) value;
        } else if (value != null) {
            return String.valueOf(value);
        }

        return null;
    }

    private int getInt(String key, int fallback) {
        Object value = get(key);

        if (value instanceof Integer) {
            return (int) value;
        } else if (value != null) {
            try {
                return Integer.valueOf((String) value);
            } catch (NumberFormatException e) {

            }
        }

        return fallback;
    }

    private String[] getStringArray(String key) {
        Object value = get(key);

        if (value instanceof String[]) {
            return (String[]) value;
        }

        return null;
    }


}
