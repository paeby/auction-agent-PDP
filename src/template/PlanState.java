package template;

/**
 * Created by Alexis Semple on 28/11/15.
 */

import logist.task.Task;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * State implementation
 */
public class PlanState implements Comparable<PlanState> {

    private Integer[] firstPickup;
    private Integer[] timeP; // [p0, p1, ..., pn]
    private Integer[] timeD; // [d0, d1, ..., dn]
    private int[][] load;
    private List<MyVehicle> vehicles;
    private Map<Integer, HashSet<Integer>> vTasks = new HashMap<Integer, HashSet<Integer>>(); // Map from vehicle_id to Set of tasks in vehicle's track
    //private double cost;
    public double[] cost;
    public final int taskSize;

    /**
     * Constructor for empty PlanState
     * @param vehicles
     * @param taskSize
     */
    public PlanState(List<MyVehicle> vehicles, int taskSize) {
        cost = new double[vehicles.size()];
        firstPickup = new Integer[vehicles.size()];
        timeP = new Integer[taskSize];
        for(int i = 0; i < taskSize; i++)timeP[i] = 0;
        timeD = new Integer[taskSize];
        for(int i = 0; i < taskSize; i++)timeD[i] = 0;
        load = new int[vehicles.size()][2 * taskSize];
        this.vehicles = vehicles;
        this.taskSize = taskSize;
        for(MyVehicle v: vehicles) vTasks.put(v.id(), new HashSet<Integer>());
    }

    /**
     * Constructor that copies a plan
     * @param p plan to be copied to new plan
     */
    public PlanState(PlanState p) {
        this.cost = p.cost.clone();
        this.taskSize = p.taskSize;
        firstPickup = p.getFirstPickup().clone();
        timeP = p.getTimeP().clone();
        timeD = p.getTimeD().clone();
        this.load = new int[p.vehicles.size()][2 * p.taskSize];
        for (int i = 0; i < p.vehicles.size(); i++) {
            for (int j = 0; j < p.taskSize * 2; j++) {
                this.load[i][j] = p.getLoad()[i][j];
            }
        }
        this.vehicles = p.vehicles;
        //Copy of HashSet values in Map
        for (MyVehicle v : vehicles) vTasks.put(v.id(), new HashSet<Integer>());
        for(Map.Entry<Integer, HashSet<Integer>> e: p.getVTasks().entrySet()) {
            for(Integer i: e.getValue()) {
                vTasks.get(e.getKey()).add(new Integer(i));
            }
        }
    }

    public Integer[] getFirstPickup() {
        return firstPickup;
    }

    public Integer[] getTimeP() {
        return timeP;
    }

    public Integer[] getTimeD() {
        return timeD;
    }

    public int[][] getLoad() {
        return load;
    }

    /**
     * @param v vehicle
     * @return set for given vehicle v
     */
    public HashSet<Integer> getVTasks(MyVehicle v) {
        return vTasks.get(v.id());
    }

    public Map<Integer, HashSet<Integer>> getVTasks() {
        return vTasks;
    }

    public void addVTasks(Integer v, Integer t) {
        vTasks.get(v).add(t);
    }

    public void removeVTasks(Integer v, Integer t) {
        vTasks.get(v).remove(t);
    }

    public double cost() {
        double costTot = 0;
        for(int i = 0; i < vehicles.size(); i++) {
            costTot += cost[i];
        }
        return costTot;
    }

    @Override
    public int compareTo(PlanState o) {
        return Double.compare(this.cost(),o.cost());
    }

}
