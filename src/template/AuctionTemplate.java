package template;

//the list of imports
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import logist.LogistSettings;
import logist.agent.Agent;
import logist.config.Parsers;
import logist.behavior.AuctionBehavior;
import logist.simulation.Vehicle;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

/**
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 * 
 */
@SuppressWarnings("unused")
public class AuctionTemplate implements AuctionBehavior {

	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;
	private Random random;
	private Vehicle vehicle;
	private City currentCity;
	//Growing Set of tasks with each new task auctioned
	private IncrementalAgent myAgent;
	private IncrementalAgent potentialAgent;
	private ArrayList<IncrementalAgent> opponents;
	private ArrayList<IncrementalAgent> potentialOpponents;
	//Time allowed to compute bid
	private long MAX_TIME;
	private static Planner planner; //TODO use centralized agent for this class. Make it static object?
	private double myTotalBid;
	private double opponentTotalBid;
	private double ratio;
	private double moderate; //TODO implement in auctionResult and change name!!!
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
			ls = Parsers.parseSettings("config/settings_default.xml");
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
		//setup here for incremental collections?
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

		moderate = 0.5; //TODO why these values at init?
		// I think it is because we want to get more tasks at the beginning
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

		//Partition time according
		int totalTasks = potentialAgent.getTaskSize();
		for (IncrementalAgent a: potentialOpponents) totalTasks += a.getTaskSize();
		assert totalTasks != 0; //else divide by zero
		int myTime = (int) Math.ceil(potentialAgent.getTaskSize() / totalTasks * MAX_TIME);
		int opponentTime = (int)MAX_TIME - myTime / 3;

		//Change costs of new IncrementalAgents
		potentialAgent.setCost(planner.getCost(potentialAgent));
		double oppMeanCost = 0;
		for (IncrementalAgent a: potentialOpponents) {
			a.setCost(planner.getCost(a));
			oppMeanCost += a.getCost();
		}
		oppMeanCost /= potentialOpponents.size();

		//Compute marginal costs for me and for opponent
		double marginalCost = myAgent.getCost() - potentialAgent.getCost();
		double oppMargCost = oppMeanCost - oppPrevMeanCost;

		//In order to bid lower than opponents estimated bid
		double bid = 0.85 * oppMargCost;
		if(bid < marginalCost * moderate) bid = marginalCost * moderate;
		if(bid < 0) bid = 1;
		return (long) Math.ceil(bid);
	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
		
//		System.out.println("Agent " + agent.id() + " has tasks " + tasks);

		Plan planVehicle1 = naivePlan(vehicle, tasks);

		List<Plan> plans = new ArrayList<Plan>();
		plans.add(planVehicle1);
		while (plans.size() < vehicles.size())
			plans.add(Plan.EMPTY);

		return plans;
	}

	private Plan naivePlan(Vehicle vehicle, TaskSet tasks) {
		City current = vehicle.getCurrentCity();
		Plan plan = new Plan(current);

		for (Task task : tasks) {
			// move: current city => pickup location
			for (City city : current.pathTo(task.pickupCity))
				plan.appendMove(city);

			plan.appendPickup(task);

			// move: pickup location => delivery location
			for (City city : task.path())
				plan.appendMove(city);

			plan.appendDelivery(task);

			// set current city
			current = task.deliveryCity;
		}
		return plan;
	}
}
