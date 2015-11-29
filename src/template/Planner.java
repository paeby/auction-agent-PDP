package template;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import logist.plan.Plan;
import logist.task.Task;
import logist.topology.Topology.City;

/**
 * Created by Prisca Aeby on 27/11/15.
 */

public class Planner {
    private long timeout_plan;
    private int iterations;
    private PlanState bestPlan;

    public Planner(long timeout, int iterations) {
        this.timeout_plan = timeout;
        this.iterations = iterations;
    }

    public double getCost(IncrementalAgent agent) {
        PlanState plan = new PlanState(agent.getVehicles(), agent.getTasks());
        initSolution(agent.getVehicles(), agent.getTasks(), plan);
        long time_start = System.currentTimeMillis();
        double min = Integer.MAX_VALUE;
        double planCost;
        double lastCost;
        int counter =0;
        List<PlanState> neighbours;
        //cost and load initialized
        for(MyVehicle v: agent.getVehicles()) {
            updateLoad(plan, v);
        }

        for (int i = 0; i < iterations; i++) {
            neighbours = ChooseNeighbours(plan, agent.getTasks(), agent.getVehicles());
            if(System.currentTimeMillis()-time_start > this.timeout_plan) {
                System.out.println("time out centralized plan");
                break;
            }
            lastCost = plan.cost();
            plan = localChoice(neighbours, plan, i);
            planCost = plan.cost();

            if(lastCost == planCost){
                counter ++;
                if(counter == 1){
                    neighbours = (moreNeighbours(agent.getVehicles(), plan));
                    Collections.sort(neighbours);
                    plan = neighbours.get(0);
                }
                if(counter == 2){
                    neighbours = (moreNeighbours(agent.getVehicles(), plan));
                    plan = localChoice(neighbours, plan, i);
                }
            }
            else{
                counter = 0;
            }
            if(planCost < min) {
                bestPlan = new PlanState(plan);
                min = planCost;

            }
        }
        return bestPlan.cost();
    }

    public List<Plan> getPlan(IncrementalAgent agent) {
        List<Plan> vplans = new ArrayList<>();
        for (MyVehicle v: agent.getVehicles()) {
            vplans.add(v.id(), buildPlan(bestPlan, v, agent.getTasks()));
        }
        return vplans;
    }

    public Plan buildPlan(PlanState state, MyVehicle v, HashSet<Task> tasks) {
        Integer next = state.getFirstPickup()[v.getVehicle().id()];
        if(next != null) {
            Task t = getTask(tasks, next);
            City current = v.getHome();
            Plan p = new Plan(current);
            Integer[] times = new Integer[state.getTimeP().length + state.getTimeD().length];
            System.arraycopy(state.getTimeP(), 0, times, 0, state.getTimeP().length);
            System.arraycopy(state.getTimeD(), 0, times, state.getTimeP().length, state.getTimeD().length);
            ArrayIterator it = new ArrayIterator(times, state.getVTasks(v));

            while(it.hasNext()) {
                int time = it.next();
                int pickupIndex = findIndex(v, state, state.getTimeP(), time);
                int deliverIndex = findIndex(v, state, state.getTimeD(), time);

                if(pickupIndex != -1) {
                    for (City c: current.pathTo(getTask(tasks, pickupIndex).pickupCity)){
                        p.appendMove(c);
                    }
                    current = getTask(tasks, pickupIndex).pickupCity;
                    p.appendPickup(getTask(tasks, pickupIndex));

                } else {
                    for (City c: current.pathTo(getTask(tasks, deliverIndex).deliveryCity)){
                        p.appendMove(c);
                    }
                    current = getTask(tasks, deliverIndex).deliveryCity;
                    p.appendDelivery(getTask(tasks, deliverIndex));
                }
            }
            return p;
        }
        else {
            return Plan.EMPTY;
        }
    }


    /**
     * Gets the Task from set with task_id id
     * @param tasks set
     * @param id id
     * @return task with id
     */
    private Task getTask(HashSet<Task> tasks, int id) {
        for (Task t : tasks) {
            if (t.id == id) {
                return t;
            }
        }
        return null;
    }


    private Integer findIndex(MyVehicle v, PlanState plan, Integer[] times, Integer t) {
        for(int i = 0; i < times.length; i++) {
            if(times[i] == t && plan.getVTasks(v).contains(i)) return i;
        }
        return -1;
    }

    // give tasks to nearest vehicle
    private void initSolution(List<MyVehicle> vehicles, HashSet<Task> tasks, PlanState plan) {
        City[] cities = new City[vehicles.size()];
        Integer[] times = new Integer[vehicles.size()];
        for(MyVehicle v: vehicles) {
            cities[v.id()] = v.getHome();
            times[v.id()] = 0;
        }

        for(Task t : tasks) {
            double min = Double.MAX_VALUE;
            MyVehicle vChosen = null;
            for(MyVehicle v: vehicles) {
                double cost = cities[v.id()].distanceTo(t.pickupCity) * v.getVehicle().costPerKm();
                if(cost < min && v.getCapacity() >= t.weight)  {
                    min = cost;
                    vChosen = v;
                }
            }
            plan.addVTasks(vChosen.id(), t.id);
            plan.getTimeP()[t.id] = times[vChosen.id()];
            plan.getTimeD()[t.id] = times[vChosen.id()] + 1;
            times[vChosen.id()] = times[vChosen.id()] + 2;
            if(plan.getFirstPickup()[vChosen.id()] == null) {
                plan.getFirstPickup()[vChosen.id()] = t.id;
            }
            cities[vChosen.id()] = t.deliveryCity;
        }
        for(MyVehicle v: vehicles) updateLoad(plan, v);
    }

    /**
     * Chooses best option (lowest cost) in list of neighbours
     * @param neighbours list of neighbours from current state
     * @param i
     * @return Best plan option of this iteration
     */
    private PlanState localChoice(List<PlanState> neighbours, PlanState old, int i) {
        double cost = old.cost();
        Random rand = new Random();
        PlanState best;
        int tries = neighbours.size();

        while(tries--> 0) {
            best = neighbours.get(rand.nextInt(neighbours.size()));
            double diff = cost - best.cost();
            if(diff > 0) return best;
            else if(rand.nextDouble() < (Math.exp(diff / ((iterations) / (double) i)))) return best;
        }
        return old;
    }

    /**
     * Computes all PlanState neighbours from given PlanState by exchanging tasks between vehicles, and permuting within a vehicle
     * @param plan current plan
     * @param tasks set of all tasks
     * @param vehicles set of all vehicles
     * @return a list of PlanStates that are neighbours of the current plan
     */
    private List<PlanState> ChooseNeighbours(PlanState plan, HashSet<Task> tasks, List<MyVehicle> vehicles){
        List<PlanState> neighbours = new ArrayList<PlanState>();
        MyVehicle v;

        //Pick random vehicle
        int nTasks;
        do {
            v = vehicles.get(new Random().nextInt(vehicles.size()));
            nTasks = plan.getVTasks(v).size();
        } while(nTasks < 1);

        // Change random task with all other vehicles
        HashSet<Integer> vTasks = plan.getVTasks(v);
        int size = vTasks.size();
        int item = new Random().nextInt(size);
        int i = 0;
        Integer task = 0;
        for(Integer obj : vTasks){
            if (i == item) task = obj;
            i = i + 1;
        }

        for(MyVehicle v2: vehicles) {
            if(getTask(tasks, task).weight <= v2.getCapacity()){
                neighbours.addAll(changeVehicle(v, v2, task, plan));
            }
        }

        return neighbours;
    }


    private List<PlanState> moreNeighbours(List<MyVehicle> vehicles, PlanState plan) {
        List<PlanState> neighbours = new ArrayList<PlanState>();

        for(MyVehicle v1: vehicles) {
            for(MyVehicle v2: vehicles) {
                if(plan.getVTasks(v1).size()>0){
                    HashSet<Integer> vTasks = plan.getVTasks(v1);
                    int size = vTasks.size();
                    int item = new Random().nextInt(size);
                    int i = 0;
                    Integer task = 0;
                    for(Integer obj : vTasks){
                        if (i == item) task = obj;
                        i = i + 1;
                    }
                    neighbours.addAll(changeVehicle(v1, v2, task, plan));
                }
            }
        }
        return neighbours;
    }

    /**
     * Checks the validity of pickup and delivery time arrays
     * @param pickup array for pickup times of tasks, with each id = task_id and pickup[id] the time at which it is picked up
     * @param delivery array for delivery times of tasks, idem pickup
     * @param tasks task ids to be to be checked, i.e. the tasks belonging to a specific vehicle's route
     * @return true if all pickups of tasks happen before their delivery, else false
     */
    private boolean checkTimes(Integer[] pickup, Integer[] delivery, HashSet<Integer> tasks) {
        for(Integer t: tasks) {
            if(pickup[t] > delivery[t]) return false;
        }
        return true;
    }

    /**
     * Updates the load values of a plan for a given vehicle
     * @param plan to be updated
     * @param vehicle for which the task-permutation has changed
     * @return true if load is legal, else false
     */
    private boolean updateLoad(PlanState plan, MyVehicle vehicle) {
        int vID = vehicle.id();
        int cost = 0;
        Integer next = plan.getFirstPickup()[vID];
        if(next != null) {
            Integer[] times = new Integer[plan.getTimeP().length + plan.getTimeD().length];
            System.arraycopy(plan.getTimeP(), 0, times, 0, plan.getTimeP().length);
            System.arraycopy(plan.getTimeD(), 0, times, plan.getTimeP().length, plan.getTimeD().length);
            ArrayIterator it = new ArrayIterator(times, plan.getVTasks(vehicle));

            for(int i = 0; i < 2*plan.tasks.size(); i++) {
                plan.getLoad()[vID][i] = 0;
            }

            int load = 0;
            City current = vehicle.getHome();
            while(it.hasNext()) {
                int time = it.next();
                int pickupIndex = findIndex(vehicle, plan, plan.getTimeP(), time);
                int deliverIndex = findIndex(vehicle, plan, plan.getTimeD(), time);

                if(pickupIndex != -1) {
                    load += getTask(plan.tasks, pickupIndex).weight;
                    plan.getLoad()[vID][time] = load;
                    cost += current.distanceTo(getTask(plan.tasks, pickupIndex).pickupCity)*vehicle.getVehicle().costPerKm();
                    current = getTask(plan.tasks, pickupIndex).pickupCity;
                    if(load > vehicle.getCapacity()) return false;

                } else {
                    load -= getTask(plan.tasks, deliverIndex).weight;
                    plan.getLoad()[vID][time] = load;
                    cost += current.distanceTo(getTask(plan.tasks, deliverIndex).deliveryCity)*vehicle.getVehicle().costPerKm();
                    current = getTask(plan.tasks, deliverIndex).deliveryCity;
                }
            }
        }
        plan.cost[vehicle.id()] = cost;
        return true;
    }

    /**
     * Moves first task in set of v1 to first task in set of v2 and makes necessary updates
     * @param v1 first vehicle
     * @param v2 second vehicle
     * @param task task id
     * @param plan current plan
     * @return List of neighbours with task changed and all possible delivery times
     */
    private List<PlanState> changeVehicle(MyVehicle v1, MyVehicle v2, Integer task, PlanState plan) {
        List<PlanState> neighbours = new ArrayList<PlanState>();
        PlanState neighbour = new PlanState(plan);
        neighbour.removeVTasks(v1.id(), task);
        neighbour.addVTasks(v2.id(), task);

        int deliverTask = neighbour.getTimeD()[task];
        int pickupTask = neighbour.getTimeP()[task];

        // update times v1 after removing first task
        for(Integer i: neighbour.getVTasks(v1)) {
            if(plan.getTimeP()[i]>deliverTask) neighbour.getTimeP()[i] -= 1;
            if(plan.getTimeD()[i]>deliverTask) neighbour.getTimeD()[i] -= 1;
            if(plan.getTimeP()[i]>pickupTask) neighbour.getTimeP()[i] -= 1;
            if(plan.getTimeD()[i]>pickupTask) neighbour.getTimeD()[i] -= 1;
        }

        boolean found = false;
        for (Integer i: neighbour.getVTasks(v1)){  // Update nextPickup for v1 to the task after the one just removed
            if (neighbour.getTimeP()[i] == 0){
                neighbour.getFirstPickup()[v1.id()] = i;
                found = true;
            }
        }
        if(!found) {
            neighbour.getFirstPickup()[v1.id()] = null ;
        }

        int n = 2*neighbour.getVTasks(v2).size();

        for(int d = 1; d < n; d++) {
            for(int p = 0; p < d; p++){
                PlanState newNeighbour = new PlanState(neighbour);
                for(Integer i: neighbour.getVTasks(v2)) {
                    if(i != task){
                        if(neighbour.getTimeP()[i] >= p)newNeighbour.getTimeP()[i]+=1;
                        if(neighbour.getTimeD()[i] >= p)newNeighbour.getTimeD()[i]+=1;
                    }
                }
                for(Integer i: neighbour.getVTasks(v2)) {
                    if(i != task){
                        if(newNeighbour.getTimeP()[i] >= d)newNeighbour.getTimeP()[i]+=1;
                        if(newNeighbour.getTimeD()[i] >= d)newNeighbour.getTimeD()[i]+=1;
                    }
                }
                newNeighbour.getTimeD()[task] = d;
                newNeighbour.getTimeP()[task] = p;
                found = false;

                for (Integer i: newNeighbour.getVTasks(v2)){  // Update nextPickup for v2 to the task after the one just removed
                    if (newNeighbour.getTimeP()[i] == 0){
                        newNeighbour.getFirstPickup()[v2.id()] = i;
                        found = true;
                    }
                }
                if(!found) {
                    newNeighbour.getFirstPickup()[v1.id()] = null ;
                }

                if(updateLoad(newNeighbour, v1) && updateLoad(newNeighbour, v2)){
                    neighbours.add(newNeighbour);
                }
            }
        }
        return neighbours;
    }


    /**
     * State implementation
     *//*
    public class PlanState implements Comparable<PlanState> {

        private Integer[] firstPickup;
        private Integer[] timeP; // [p0, p1, ..., pn]
        private Integer[] timeD; // [d0, d1, ..., dn]
        private int[][] load;
        private List<MyVehicle> vehicles;
        private HashSet<Task> tasks;
        private Map<Integer, HashSet<Integer>> vTasks = new HashMap<Integer, HashSet<Integer>>(); // Map from vehicle_id to Set of tasks in vehicle's track
        //private double cost;
        private double[] cost;

        *//**
         * Constructor for empty PlanState
         * @param vehicles
         * @param tasks
         *//*
        public PlanState(List<MyVehicle> vehicles, HashSet<Task> tasks) {
            cost = new double[vehicles.size()];
            firstPickup = new Integer[vehicles.size()];
            timeP = new Integer[tasks.size()];
            for(int i = 0; i < tasks.size(); i++)timeP[i] = 0;
            timeD = new Integer[tasks.size()];
            for(int i = 0; i < tasks.size(); i++)timeD[i] = 0;
            load = new int[vehicles.size()][2 * tasks.size()];
            this.vehicles = vehicles;
            this.tasks = tasks;
            for(MyVehicle v: vehicles) vTasks.put(v.id(), new HashSet<Integer>());
        }

        *//**
         * Constructor that copies a plan
         * @param p plan to be copied to new plan
         *//*
        public PlanState(PlanState p) {
            this.cost = p.cost.clone();
            firstPickup = p.getFirstPickup().clone();
            timeP = p.getTimeP().clone();
            timeD = p.getTimeD().clone();
            this.load = new int[p.vehicles.size()][2 * p.tasks.size()];
            for (int i = 0; i < p.vehicles.size(); i++) {
                for (int j = 0; j < p.tasks.size() * 2; j++) {
                    this.load[i][j] = p.getLoad()[i][j];
                }
            }
            this.tasks = p.tasks;
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

        *//**
         * @param v vehicle
         * @return set for given vehicle v
         *//*
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

    *//**
     * Iterates over an int array from min to max given the tasks of a vehicle
     *//*
    class ArrayIterator implements Iterator<Object> {

        private List<Integer> l;
        private HashSet<Integer> tasks = new HashSet<Integer>();

        ArrayIterator(Integer[] time, HashSet<Integer> t) {
            l = new ArrayList<Integer>(Collections.nCopies(time.length, -1));
            for (Integer i: t) {
                tasks.add(new Integer(i));
            }
            for(Integer ti: tasks) {
                l.set(ti, time[ti]);
                l.set(ti + time.length/2, time[ti + time.length/2]);
            }
        }

        @Override
        public boolean hasNext() {
            for (Integer i: tasks) {
                if(l.get(i) != -1 || l.get(i+l.size()/2) != -1) return true;
            }
            return false;
        }

        @Override
        public Integer next() {
            int min = Integer.MAX_VALUE;
            Integer index = -1;

            for(Integer t: tasks) {
                if(l.get(t) != -1 && l.get(t) < min) {
                    min = l.get(t);
                    index = t;
                }
                if(l.get(t+l.size()/2) != -1 && l.get(t+l.size()/2) < min) {
                    min = l.get(t+l.size()/2);
                    index = t+l.size()/2;
                }
            }

            l.set(index, -1); //remove
            return min;
        }

        @Override
        public void remove() {

        }
    }*/

    public void setTimeout(long timeout) {
        this.timeout_plan = timeout;
    }
}



