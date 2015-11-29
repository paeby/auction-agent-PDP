package template;

import logist.LogistSettings;
import logist.agent.Agent;
import logist.behavior.AuctionBehavior;
import logist.config.Parsers;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/**
 * Created by Alexis Semple on 29/11/15.
 */
public class MyAuction  implements AuctionBehavior {
    private Topology topology;
    private TaskDistribution distribution;
    private Agent agent;
    private Random random;
    private Vehicle vehicle;
    private Topology.City currentCity;
    //Growing Set of tasks with each new task auctioned
    private IncrementalAgent myAgent; //object used to incrementally add tasks to set and compute its cost as auction proceeds
    private IncrementalAgent potentialAgent; //used in bidding phase to compute 'potential' new stage in iteration (if bid is won)
    private ArrayList<IncrementalAgent> opponents = new ArrayList<>(); //n opponents incrementally augmented
    private ArrayList<IncrementalAgent> potentialOpponents = new ArrayList<>(); //n potential opponents for bidding phase
    //Time allowed to compute bid
    private static long MAX_TIME;
    private static Planner planner;
    private double myTotalBid; //total of bids that were accepted - reward for tasks
    private double opponentTotalBid; //total of bids of opponent that were accepted
    private double ratio;
    private double moderate;
    private ArrayList<Double> opponentBidRatio;
    private int round;
    private int iterations;

    @Override
    public void setup(Topology topology, TaskDistribution distribution,
                      Agent agent) {
        //get time-outs

        // this code is used to get the timeouts
        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config/settings_auction.xml");
        } catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }

        // the plan method cannot execute more than timeout_plan milliseconds
        MAX_TIME = ls.get(LogistSettings.TimeoutKey.BID)-1000;
        iterations = 10000;
        this.topology = topology;
        this.distribution = distribution;
        this.agent = agent;
        this.vehicle = agent.vehicles().get(0);
        this.currentCity = vehicle.homeCity();
        //setup here for incremental collections
        myAgent = new IncrementalAgent(agent.vehicles());
        //3 different settings for opponents
        int opponentSize = 3;
        for (int i = 0; i < opponentSize; i++) {
            opponents.add(new IncrementalAgent(agent.vehicles()));

            opponents.get(i).randomizeVehicles();

        }
        opponentBidRatio = new ArrayList<>();
        myTotalBid = 0;
        opponentTotalBid = 0;

        planner = new Planner(MAX_TIME, iterations);

        moderate = 0.5;
        ratio = 1;

        long seed = -9019554669489983951L * currentCity.hashCode() * agent.id();
        this.random = new Random(seed);
    }

    @Override
    public void auctionResult(Task previous, int winner, Long[] bids) {
        long myBid = bids[agent.id()];
        long opponentBid = bids[(agent.id() + 1) % 2];

        boolean weWon = agent.id() == winner;

        //If I am winner, add task to my set, else add to opponent's sets
        if(weWon) {
            //myAgent = the object computed for potentialAgent during bidding
            myAgent = new IncrementalAgent(potentialAgent.getVehicles(), potentialAgent.getTasks(), potentialAgent.getCost());
            myTotalBid += myBid;
        }
        else {
            //opponents = the objects computed for potentialOpponents during bidding
            for (int i = 0; i < opponents.size(); i++) {
                opponents.get(i).addTask(previous);
                IncrementalAgent potentialOpp = potentialOpponents.get(i);
                opponents.set(i, new IncrementalAgent(potentialOpp.getVehicles(), potentialOpp.getTasks(), potentialOpp.getCost()));
            }
            opponentTotalBid += opponentBid;
        }

        double opponentMeanCost = 0;
        for (IncrementalAgent a: opponents) {
            opponentMeanCost += a.getCost();
        }
        opponentMeanCost /= opponents.size();
        opponentBidRatio.add(opponentTotalBid / opponentMeanCost);

        round++; //increment round counter

        int weight = 0; //TODO what is happening here? unclear...
        double newRatio = 0;
        for (int i = 1; i <= round; i++) {
            newRatio += i * opponentBidRatio.get(i-1);
            weight += i;
        }

        ratio = (newRatio/weight + ratio) / 2;
        if(ratio > 2.5) ratio = 2.5; //TODO test these values!!
        else if(ratio < 0.7) ratio = 0.7;

        moderate += (weWon ? 0.1 : -0.05); //TODO test these values!!
        if(moderate > 1) moderate = 1;
        else if(moderate < 0.5) moderate = 0.5;
    }

    @Override
    public Long askPrice(Task task) {
        //If agent cannot carry task, bid highest so won't take task
        if (myAgent.maxCapacity() < task.weight) return Long.MAX_VALUE;

        //Get cost of opponents before modifying for marginal cost later
        double oppPrevMeanCost = 0;
        for (IncrementalAgent a: opponents) oppPrevMeanCost += a.getCost();
        oppPrevMeanCost /= opponents.size();

        //Compute marginal cost
        //Add task to taskset, recompute plan
        potentialAgent = myAgent.copyOf();
        //We only add to currentTasks if bid is won in auctionResult(...)
        potentialAgent.addTask(task);

        //Add task to potential opponent's tasksets
        potentialOpponents = new ArrayList<>();
        for(IncrementalAgent a: opponents) {
            potentialOpponents.add(a.copyOf());
            //Add only if biggest vehicle can carry task
            if (potentialOpponents.get(potentialOpponents.size()-1).maxCapacity() >= task.weight)
                potentialOpponents.get(potentialOpponents.size()-1).addTask(task);
        }

        //Partition time according to number of tasks in sets of each
        int totalTasks = potentialAgent.getTaskSize();
        for (IncrementalAgent a: potentialOpponents) totalTasks += a.getTaskSize();
        assert totalTasks != 0; //else divide by zero
        int myTime = (int) Math.ceil(potentialAgent.getTaskSize() / totalTasks * MAX_TIME);
        int opponentTime = (int)MAX_TIME - myTime / 3;

        //Change costs of new IncrementalAgents
        planner.setTimeout(myTime);
        potentialAgent.setCost(planner.getCost(potentialAgent));
        double oppMeanCost = 0;
        planner.setTimeout(opponentTime);
        for (IncrementalAgent a: potentialOpponents) {
            a.setCost(planner.getCost(a));
            oppMeanCost += a.getCost();
        }
        oppMeanCost /= potentialOpponents.size();

        //Compute marginal costs for me and for opponent
        double marginalCost = potentialAgent.getCost() - myAgent.getCost();
        double oppMargCost = (oppMeanCost - oppPrevMeanCost) * ratio;

        //In order to bid lower than opponents estimated bid
        double bid = 0.85 * oppMargCost;
        if(bid < marginalCost * moderate) bid = marginalCost * moderate;
        if(bid < 0) bid = 1;
        return (long) Math.ceil(bid);
    }

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {

//		System.out.println("Agent " + agent.id() + " has tasks " + tasks);

        List<MyVehicle> myVehicles = new ArrayList<>();
        for(Vehicle v: vehicles) {
            myVehicles.add(new MyVehicle(v));
        }

        HashSet<Task> myTasks = new HashSet<>();
        for(Task t: tasks) {
            myTasks.add(t);
        }

        PlanState myPlanState = new PlanState(myVehicles, tasks.size());

        //Plan planVehicle1 = naivePlan(vehicle, tasks);

        List<Plan> plans = new ArrayList<Plan>();

        for(MyVehicle v: myVehicles) {
            plans.add(planner.buildPlan(myPlanState, v, myTasks));
        }

		/*plans.add(planVehicle1);
		while (plans.size() < vehicles.size())
			plans.add(Plan.EMPTY);*/

        return plans;
    }
}
