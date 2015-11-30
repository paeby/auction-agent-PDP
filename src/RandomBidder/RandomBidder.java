package RandomBidder;

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
import template.Planner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/**
 * Created by Alexis Semple on 30/11/15.
 */
public class RandomBidder implements AuctionBehavior {

    private Agent agent;
    private Random random;
    private Vehicle vehicle;
    private Topology.City currentCity;
    private long timeout_plan;
    private Planner planner;

    @Override
    public void setup(Topology topology, TaskDistribution distribution,
                      Agent agent) {

        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config/settings_auction.xml");
        } catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }
        timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN)-500;
        planner = new Planner(ls.get(LogistSettings.TimeoutKey.BID)-500, 10000);
        planner.setTimeout(timeout_plan);
        this.agent = agent;
        this.vehicle = agent.vehicles().get(0);
        this.currentCity = vehicle.homeCity();

        long seed = -9019554669489983951L * currentCity.hashCode() * agent.id();
        this.random = new Random(seed);
    }

    @Override
    public void auctionResult(Task previous, int winner, Long[] bids) {
        if (winner == agent.id()) {
            currentCity = previous.deliveryCity;
        }
    }

    @Override
    public Long askPrice(Task task) {

        long bid =random.nextInt(5000) + 5;
        return bid;
    }

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {

        List<template.MyVehicle> vs = new ArrayList<>();
        for (Vehicle v: vehicles) {
            vs.add(new template.MyVehicle(v));
        }
        HashSet<Task> ts = new HashSet<>();
        for(Task t: tasks) {
            ts.add(t);
        }
        IncrementalAgent finalAgent = new IncrementalAgent(vs, ts, 0);
        planner.getCost(finalAgent);
        return planner.getPlan(finalAgent);
    }
}
