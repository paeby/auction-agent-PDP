package template;
import java.util.*;

import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.topology.Topology.City;

/**
 * Created by Prisca Aeby on 27/11/15.
 */

public class Planner {
    private long timeout_plan;
    private int iterations;
    private PlanState bestPlan;
    private Map<Integer, Task> tasksMap = new HashMap<>();
    private Map<Task, Integer> tasksID = new HashMap<>();
    public Planner(long timeout, int iterations) {
        this.timeout_plan = timeout;
        this.iterations = iterations;
    }

    public double getCost(IncrementalAgent agent) {
        // mapping the tasks' id's from 0 to tasks.size to be able to use time arrays
        List<Task> tasksList = new ArrayList<>(agent.getTasks());
        tasksMap = new HashMap<>();
        tasksID = new HashMap<>();
        for(int i = 0; i < tasksList.size(); i++) {
            tasksMap.put(i, tasksList.get(i));
            tasksID.put(tasksList.get(i), i);
        }
        // mapping the vehicles' id's from 0 to vehicles.size to be able to use time arrays
        List<MyVehicle> vehicleList = new ArrayList<>(agent.getVehicles());
        for(int i = 0; i < vehicleList.size(); i++) vehicleList.get(i).setId(i);

        PlanState plan = new PlanState(agent.getVehicles(), tasksList.size());
        initSolution(agent.getVehicles(), plan);
        long time_start = System.currentTimeMillis();
        double min = Integer.MAX_VALUE;
        double planCost;
        double lastCost;
        int counter = 0;
        bestPlan = new PlanState(plan);
        List<PlanState> neighbours;
        //cost and load initialized
        for(MyVehicle v: agent.getVehicles()) {
            updateLoad(plan, v);
        }
        for (int i = 0; i < iterations; i++) {
            neighbours = ChooseNeighbours(plan, agent.getVehicles());
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
            if(System.currentTimeMillis()-time_start > this.timeout_plan) {
                System.out.println("time out centralized plan");
                break;
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
            Task t = tasksMap.get(next);
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
                    for (City c: current.pathTo(tasksMap.get(pickupIndex).pickupCity)){
                        p.appendMove(c);
                    }
                    current = tasksMap.get(pickupIndex).pickupCity;
                    p.appendPickup(tasksMap.get(pickupIndex));

                } else {
                    for (City c: current.pathTo(tasksMap.get(deliverIndex).deliveryCity)){
                        p.appendMove(c);
                    }
                    current = tasksMap.get(deliverIndex).deliveryCity;
                    p.appendDelivery(tasksMap.get(deliverIndex));
                }
            }
            return p;
        }
        else {
            return Plan.EMPTY;
        }
    }

    private Integer findIndex(MyVehicle v, PlanState plan, Integer[] times, Integer t) {
        for(int i = 0; i < times.length; i++) {
            if(times[i] == t && plan.getVTasks(v).contains(i)) return i;
        }
        return -1;
    }

    // give tasks to nearest vehicle
    private void initSolution(List<MyVehicle> vehicles, PlanState plan) {
        City[] cities = new City[vehicles.size()];
        Integer[] times = new Integer[vehicles.size()];
        for(MyVehicle v: vehicles) {
            cities[v.id()] = v.getHome();
            times[v.id()] = 0;
        }

        for(Task t : tasksMap.values()) {
            double min = Double.MAX_VALUE;
            MyVehicle vChosen = null;
            for(MyVehicle v: vehicles) {
                double cost = cities[v.id()].distanceTo(t.pickupCity) * v.getVehicle().costPerKm();
                if(cost < min && v.getCapacity() >= t.weight)  {
                    min = cost;
                    vChosen = v;
                }
            }

            plan.addVTasks(vChosen.id(), tasksID.get(t));
            System.out.println("P length "+ plan.getTimeP().length);
            System.out.println("t id "+ tasksID.size());
            plan.getTimeP()[tasksID.get(t)] = times[vChosen.id()];
            plan.getTimeD()[tasksID.get(t)] = times[vChosen.id()] + 1;
            times[vChosen.id()] = times[vChosen.id()] + 2;
            if(plan.getFirstPickup()[vChosen.id()] == null) {
                plan.getFirstPickup()[vChosen.id()] = tasksID.get(t);
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
     * @param vehicles set of all vehicles
     * @return a list of PlanStates that are neighbours of the current plan
     */
    private List<PlanState> ChooseNeighbours(PlanState plan, List<MyVehicle> vehicles){
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
            if(tasksMap.get(task).weight <= v2.getCapacity()){
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

            for(int i = 0; i < 2*plan.taskSize; i++) {
                plan.getLoad()[vID][i] = 0;
            }

            int load = 0;
            City current = vehicle.getHome();
            while(it.hasNext()) {
                int time = it.next();
                int pickupIndex = findIndex(vehicle, plan, plan.getTimeP(), time);
                int deliverIndex = findIndex(vehicle, plan, plan.getTimeD(), time);

                if(pickupIndex != -1) {
                    load += tasksMap.get(pickupIndex).weight;
                    plan.getLoad()[vID][time] = load;
                    cost += current.distanceTo(tasksMap.get(pickupIndex).pickupCity)*vehicle.getVehicle().costPerKm();
                    current = tasksMap.get(pickupIndex).pickupCity;
                    if(load > vehicle.getCapacity()) return false;

                } else {
                    load -= tasksMap.get(deliverIndex).weight;
                    plan.getLoad()[vID][time] = load;
                    cost += current.distanceTo(tasksMap.get(deliverIndex).deliveryCity)*vehicle.getVehicle().costPerKm();
                    current = tasksMap.get(deliverIndex).deliveryCity;
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

    public void setTimeout(long timeout) {
        this.timeout_plan = timeout;
    }
}



