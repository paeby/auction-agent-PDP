package template;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import logist.simulation.Vehicle;
import logist.task.Task;

/**
 * Created by Alexis Semple on 27/11/15.
 */
public class IncrementalAgent {
    private HashSet<Task> tasks = new HashSet<>();
    private List<MyVehicle> vehicles = new ArrayList<>();
    private double cost;

    /**
     * Initial constructor
     * @param vs vehicle set
     */
    public IncrementalAgent(List<Vehicle> vs) {
        cost = 0;
        for (Vehicle v: vs) vehicles.add(new MyVehicle(v));
    }

    /**
     * Constructor for shallow copies
     * @param vs set of vehicles
     * @param ts set of tasks
     */
    public IncrementalAgent(List<MyVehicle> vs, HashSet<Task> ts, double dollahz) {
        for(Task t: ts) tasks.add(t);
        for(MyVehicle v: vs) vehicles.add(v);
        cost = dollahz;
    }

    /**
     * Add new task to the set
     * @param t task to be added
     */
    public void addTask(Task t) {
        tasks.add(t);
    }

    /**
     * Copy constructor (shallow copy of fields)
     * @return new agent
     */
    public IncrementalAgent copyOf() {
        return new IncrementalAgent(vehicles, tasks, cost);
    }

    public int getTaskSize() {
        return tasks.size();
    }

    public double getCost() {
        return cost;
    }

    public void setCost(double cost) {
        this.cost = cost;
    }

    public List<MyVehicle> getVehicles() {
        return vehicles;
    }

    public HashSet<Task> getTasks() {
        return tasks;
    }

    /**
     * Randomize how many vehicles in set
     * Randomize starting city of vehicles (neighbours
     * Randomize capacity of vehicles in set within factor range of "initial" capacity
     */
    public void randomizeVehicles() {
        Random random = new Random(System.currentTimeMillis());
        List<MyVehicle> newVehicles = new ArrayList<>();

        //random int between 2 and 5 (0 + 2 to 3 + 2)
        int numVehicles = random.nextInt(4) + 2;

        int totalCapacity = 0;

        for(MyVehicle v: vehicles) {
            totalCapacity += v.getVehicle().capacity();
        }

        //change size of vehicles to be that of numVehicles
        //randomize capacity
        for(int i = 0; i < numVehicles; i++) {
            MyVehicle prevV = vehicles.get(i % vehicles.size());
            MyVehicle newV = new MyVehicle(prevV);
            //Set vehicle capacity between factor 0.75 and 1.25 of initial capacity
            int newCapacity;
            if(i < numVehicles-1) {
                newCapacity = (int) Math.ceil(prevV.getCapacity() * (0.75 + 0.5 * random.nextDouble()));
                totalCapacity -= newCapacity;
                newV.setCapacity(newCapacity);
            }
            else newV.setCapacity(totalCapacity);
            newV.setId(i);
            newVehicles.add(newV);
        }

        //randomize home city by getting a random neighbour between 0 and 3 times
        for(MyVehicle v: newVehicles)
            for (int i = 0; i < 3; i++)
                if (random.nextDouble() < 0.5)
                    v.setHome(v.getHome().randomNeighbor(random));

        vehicles = newVehicles;
    }

    /**
     * Get the max capacity of the agent
     * @return the capacity of the biggest vehicle
     */
    public int maxCapacity() {
        int max = 0;
        for (MyVehicle v: vehicles)
            if (v.getCapacity() > max)
                max = v.getCapacity();
        return max;
    }

}
