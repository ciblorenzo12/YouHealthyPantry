package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.room.Entity;

// CORRECTED: Define a composite primary key to make each entry unique per user.
@Entity(tableName = "pantry", primaryKeys = {"barcode", "userId"})
public class Pantry {

    @NonNull
    public String barcode;

    @NonNull
    public String userId;

    public Pantry(@NonNull String barcode, @NonNull String userId) {
        this.barcode = barcode;
        this.userId = userId;
    }
}
