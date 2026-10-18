package com.f1.AI.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Driver {

    /** Jolpica/Ergast driverId, e.g. "max_verstappen". */
    @Id
    private String id;

    private String code;
    private String givenName;
    private String familyName;
    private String nationality;
    private String permanentNumber;

    protected Driver() {
    }

    public Driver(String id, String code, String givenName, String familyName, String nationality, String permanentNumber) {
        this.id = id;
        this.code = code;
        this.givenName = givenName;
        this.familyName = familyName;
        this.nationality = nationality;
        this.permanentNumber = permanentNumber;
    }

    public String getId() { return id; }
    public String getCode() { return code; }
    public String getGivenName() { return givenName; }
    public String getFamilyName() { return familyName; }
    public String getNationality() { return nationality; }
    public String getPermanentNumber() { return permanentNumber; }

    public String getFullName() { return givenName + " " + familyName; }
}
