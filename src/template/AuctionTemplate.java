package template;

//the list of imports
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import logist.behavior.AuctionBehavior;
import logist.agent.Agent;
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
	private ArrayList<IncrementalAgent> opponents;
	//Time allowed to compute bid
	private final static int MAX_TIME = 25000;
	private static Planner planner; //TODO use centralized agent for this class. Make it static object?
	private double ratio;
	private double canAllow; //TODO implement in auctionResult and change name!!!

	@Override
	public void setup(Topology topology, TaskDistribution distribution,
			Agent agent) {

		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;
		this.vehicle = agent.vehicles().get(0);
		this.currentCity = vehicle.homeCity();
		//setup here for incremental collections?
		myAgent = new IncrementalAgent(agent.vehicles());
		//3 different settings for opponents
		for (int i = 0; i < 3; i++) {
			opponents.add(new IncrementalAgent(agent.vehicles()));
			opponents.get(i).randomizeVehicles();
		}
		//TODO initialise Planner object here

		long seed = -9019554669489983951L * currentCity.hashCode() * agent.id();
		this.random = new Random(seed);
	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {
		long myBid = bids[agent.id()];
		if(agent.id() == winner)
			myAgent.addTask(previous);
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
		IncrementalAgent potentialAgent = myAgent.copyOf();
		//We only add to currentTasks if bid is won in auctionResult(...)
		potentialAgent.addTask(task);

		//Add task to potential opponent's tasksets
		ArrayList<IncrementalAgent> potentialOpponents = new ArrayList<>();
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
		int opponentTime = MAX_TIME - myTime / 3;

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
