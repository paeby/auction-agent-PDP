package template;

import logist.simulation.Vehicle;
import logist.topology.Topology.City;

/**
 * Created by Alexis Semple on 27/11/15.
 */
public class MyVehicle {
    private Vehicle vehicle;
    private City home;
    private int capacity;

    public MyVehicle(Vehicle v) {
        vehicle = v;
        home = v.homeCity();
        capacity = v.capacity();
    }

    public MyVehicle(MyVehicle v) {
        vehicle = v.getVehicle();
        home = v.getHome();
        capacity = v.getCapacity();
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public City getHome() {
        return home;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setHome(City home) {
        this.home = home;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int id() {
        return vehicle.id();
    }

    @Override
    protected MyVehicle clone() throws CloneNotSupportedException {
        return new MyVehicle(this);
    }
}
