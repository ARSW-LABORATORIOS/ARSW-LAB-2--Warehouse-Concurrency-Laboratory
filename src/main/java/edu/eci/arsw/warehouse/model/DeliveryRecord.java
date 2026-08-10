package edu.eci.arsw.warehouse.model;

public record DeliveryRecord(int position, int robotId, int parcelId, long elapsedMillis) {
}
