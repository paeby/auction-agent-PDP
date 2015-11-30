package marginalBidder;

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
import template.IncrementalAgent;
import template.MyVehicle;
import template.Planner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/**
 * Created by Alexis Semple on 29/11/15.
 */
public class MarginalBidder implements AuctionBehavior {
    private Agent agent;
    private Vehicle vehicle;
    //Growing Set of tasks with each new task auctioned
    private template.IncrementalAgent myAgent; //object used to incrementally add tasks to set and compute its cost as auction proceeds
    private template.IncrementalAgent potentialAgent; //used in bidding phase to compute 'potential' new stage in iteration (if bid is won)
    private ArrayList<template.IncrementalAgent> opponents = new ArrayList<>(); //n opponents incrementally augmented
    //Time allowed to compute bid
    private static long MAX_TIME;
    private static long timeout_plan;
    private static Planner planner;
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
        timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN)-1000;
        iterations = 10000;
        this.agent = agent;
        this.vehicle = agent.vehicles().get(0);

        //setup here for incremental collections
        myAgent = new template.IncrementalAgent(agent.vehicles());

        //3 different settings for opponents
        int opponentSize = 3;
        for (int i = 0; i < opponentSize; i++) {
            opponents.add(new template.IncrementalAgent(agent.vehicles()));
            opponents.get(i).randomizeVehicles();
        }

        planner = new Planner(MAX_TIME, iterations);
    }

    @Override
    public void auctionResult(Task previous, int winner, Long[] bids) {
        long myBid = bids[agent.id()];

        System.out.println("My bid: "+myBid);

        boolean weWon = agent.id() == winner;

        //If I am winner, add task to my set, else add to opponent's sets
        if(weWon) {
            //myAgent = the object computed for potentialAgent during bidding
            myAgent = new template.IncrementalAgent(potentialAgent.getVehicles(), potentialAgent.getTasks(), potentialAgent.getCost());
        }
    }

    @Override
    public Long askPrice(Task task) {
        //If agent cannot carry task, bid highest so won't take task
        if (myAgent.maxCapacity() < task.weight) return Long.MAX_VALUE;

        //Compute marginal cost
        //Add task to taskSet, recompute plan
        potentialAgent = myAgent.copyOf();
        //We only add to currentTasks if bid is won in auctionResult(...)
        potentialAgent.addTask(task);


        //Change costs of new IncrementalAgents
        planner.setTimeout(MAX_TIME);
        potentialAgent.setCost(planner.getCost(potentialAgent));

        //Compute marginal costs for me and for opponent
        double marginalCost = Math.abs(potentialAgent.getCost() - myAgent.getCost());
        //In order to bid lower than opponents estimated bid
        double bid = marginalCost * 1.05;
        return (long) Math.ceil(bid);
    }

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        List<MyVehicle> vs = new ArrayList<>();
        for (Vehicle v: vehicles) {
            vs.add(new MyVehicle(v));
        }
        HashSet<Task> ts = new HashSet<>();
        for(Task t: tasks) {
            ts.add(t);
        }
        IncrementalAgent finalAgent = new IncrementalAgent(vs, ts, 0);
        planner.setTimeout(timeout_plan);
        planner.getCost(finalAgent);
        return planner.getPlan(finalAgent);
    }
}
