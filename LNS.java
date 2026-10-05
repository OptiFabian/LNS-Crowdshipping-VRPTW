import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Large neighborhood search (LNS) with simulated annealing for a VRPTW in which routes can be
 * given to crowd drivers who may not show up (recourse to professional vehicles).
 * See README.md for the model.
 */
public class LNS {
	// Optional random seed, read from the "seed" system property (null = unseeded, the original behavior).
	static final Long SEED = Long.getLong("seed");
	public int num_customers = Parameters.num_customers;
	public Depot depot_start;
	public double depot_due_time; // latest return time at the depot, from the instance file
	public Solution incumbent;
	public Map<Integer,Cluster> all_clusters = new HashMap<Integer,Cluster>();
	public Map<Integer,Customer> all_customers = new HashMap<Integer,Customer>();
	public Map<Integer,Node> all_nodes = new HashMap<Integer,Node>();
	private String instance;
	public double [][] Cost;
	public double v_cost(int pre) {
		return Parameters.recourse_Vcost_1[pre];
	}
	public double f_cost(int pre) {
		return Parameters.recourse_Fcost_1[pre];
	}
	public LNS(String s) {
		instance = s;
		ReadData();
		fill_cost();
		create_clusters();
		Parameters.Cal_CD_max();
		ClarkeWright w = new ClarkeWright();
		incumbent = w.begin();
		incumbent.update_vehicle(1);
	}
	// A group of nearby customers; its penalty is the "geo" term of the insertion utility (dcm_cost).
	private class Cluster{
		public int id;
		public double penalty;
		private List<Integer> cluster_customers;
		public Cluster( List<Integer> c) {
			this.id = all_clusters.size();
			this.cluster_customers = new ArrayList<>();
			for(int i = 0; i < c.size(); i++) {
				this.cluster_customers.add(c.get(i));
			}
			all_clusters.put(this.id, this);
			if(this.id == 4){
				this.penalty = 1;
			}else{
				this.penalty = 0.5;
			}
			set_customers_geo();
		}
		public void set_customers_geo() {
			for(int i = 0; i < cluster_customers.size(); i ++) {
				all_customers.get(cluster_customers.get(i)).set_geo(this.penalty);
			}
		}
	}

	// State and operators of one LNS run: destroy, repair, simulated-annealing acceptance.
	public class LnsSearch{
		Solution solution;
		List<Integer> removed;
		Random rand;
		int iterations;
		double Temperature;
		double cool = 0.99997;
		List<Integer> wheel;
		public LnsSearch() {
			this.solution = new Solution (incumbent);
			this.removed = new ArrayList<>();
			// Optional fixed seed for reproducible runs: -Dseed=<long>.
			// Without it, the original unseeded behavior is kept (new Random() and Math.random()).
			this.rand = SEED == null ? new Random() : new Random(SEED);
			this.Temperature = 600.0;
			this.wheel = new ArrayList<>();
			for(int i = 0; i < num_customers; i++) {
				this.wheel.add(i);
			}
		}
		public void update_Temperature() {
			Temperature*=cool;
			if(Temperature < 0.003 ) {
				Temperature = 400;
				System.out.println("Reheating happens " + Temperature);
			}
		}
		
		// One iteration: destroy, repair, update the best solution, accept or reject, cool down.
		public void iterate() {
			Solution sol = new Solution (solution); 
			int q = rand.nextInt((int)Math.ceil(Parameters.num_customers*0.15)) + 5;
			double w1 = uniform();
			if(w1 < 0.7) {
				random_remove(q, sol);
			}else if (w1 < 0.8){
				remove_route(sol);
			}else if (w1 < 0.9) {
				remove_first_customers(sol);
			}else {
				remove_last_customers(sol);
			}
			sol.update_vehicle(1); // sort and price routes.
			double w = uniform();
			if(w < 0.7) {
				random_insert(sol);
			}else if(w<0.8){
				random_insert_with_noise(sol);
			}else if(w<0.9){
				insert_PV(sol);
			}else {
				insert_CD(sol);
			}
			sol.update_vehicle(1);
			if(incumbent.s_value > sol.get_solution()) {
				incumbent = sol;
			}
			if(accept(sol)) {
				solution = sol;
			}
			update_Temperature();
		}
		// Uniform draw in [0,1): Math.random() as in the original code, or the seeded generator when -Dseed is set.
		private double uniform() {
			return SEED == null ? Math.random() : rand.nextDouble();
		}
		private void random_remove(int q, Solution sol) {
			while(removed.size() < q) {
				int i = wheel.get(rand.nextInt(wheel.size()));
				if (!removed.contains(i)) {
					removed.add(i);
					if(sol.cus[i].next != null) {
						if(sol.cus[i].prev != null) {
							sol.cus[i].next.prev = sol.cus[i].prev;
							sol.cus[i].prev.next = sol.cus[i].next;
							sol.cus[i].next = null;
							sol.cus[i].prev = null;
						}else {
							for(int k = 0; k < sol.routes_0.size(); k ++) {
								if(i == sol.routes_0.get(k).start.id) {
									sol.routes_0.get(k).start = sol.cus[i].next;
									sol.cus[i].next.prev = null;
									sol.cus[i].next = null;
									sol.cus[i].prev = null;
									break;
								}
							}
						}
					}else {
						if(sol.cus[i].prev != null) {
							sol.cus[i].prev.next = null;
							sol.cus[i].prev = null;
						}else {
							for(int k = 0; k < sol.routes_0.size(); k ++) {
								if(i == sol.routes_0.get(k).start.id) {
									sol.routes_0.remove(k);
									break;
								}
							}	
						}
					}	
				}
			}
		}private void remove_route(Solution sol) {
			int k2 = rand.nextInt((int)Math.ceil(sol.routes_0.size()*0.25));
			for(int k1 = 0; k1 < 2 + k2; k1 ++) {
				if(sol.routes_0.size()==1) {
					break;}
				int k = rand.nextInt(sol.routes_0.size());
				int i = sol.routes_0.get(k).start.id;
				removed.add(i);
				Point p = sol.cus[i];
				while(p.next !=null) {
					sol.cus[i].next.prev = null;
					p = sol.cus[i].next;
					sol.cus[i].next = null;
					sol.cus[i].prev = null;
					i = p.id;
					removed.add(i);	
				}
				sol.routes_0.remove(k);
			}
		}
		private void remove_first_customers(Solution sol) {
			for(int k1 = 0; k1 < sol.routes_0.size(); k1 ++) {
				int k = (int)Math.ceil(rand.nextInt(sol.routes_0.get(k1).size)*0.5);
				if(k == sol.routes_0.get(k1).size) {continue;}
				int i = sol.routes_0.get(k1).start.id;
				Point p = sol.cus[i];
				int count =0;
				while(p.next !=null && count<k) {
					sol.cus[i].next.prev = null;
					p = sol.cus[i].next;
					sol.routes_0.get(k1).start = sol.cus[p.id];
					sol.cus[i].next = null;
					sol.cus[i].prev = null;
					removed.add(i);
					count +=1;
					i = p.id;
				}
			}
		}
		private void remove_last_customers(Solution sol) {
			for(int k1 = 0; k1 < sol.routes_0.size(); k1 ++) {
				int k = (int)Math.ceil(rand.nextInt(sol.routes_0.get(k1).size)*0.5);
				if(k == sol.routes_0.get(k1).size) {continue;}
				int i = sol.routes_0.get(k1).end.id;
				Point p = sol.cus[i];
				int count =0;
				while(p.prev !=null && count<k) {
					sol.cus[i].prev.next = null;
					p = sol.cus[i].prev;
					sol.routes_0.get(k1).end = sol.cus[p.id];
					sol.cus[i].next = null;
					sol.cus[i].prev = null;
					removed.add(i);
					count +=1;
					i = p.id;
				}
			}
		}

		/////////////////////////////////////////////////////////////////
		///////////// insertion /////////////////////////////////////////
		/////////////////////////////////////////////////////////////////
		
		// Best insertion, positions scored with the logit-style utility (dcm_cost).
		private void random_insert(Solution sol) {
			while(removed.size() > 0) {
				//insert
				Point cus1 = sol.cus[removed.get(removed.size()-1)]; //customer going to be inserted
				removed.remove(removed.size()-1);// remove customer from the list 
				Point ins = null; // best customer where it is going to be inserted
				int route = 0;
				boolean inserted = false; // 
				boolean before = false; // before or after customer
				double best_improvement = Double.MAX_VALUE; // best insertion value so far
				for(int k = 0; k < sol.routes_0.size(); k ++) {
					if(sol.routes_0.get(k).load + all_customers.get(cus1.id).d() > Parameters.capacity[0]) {
						continue;
					}
					int r_type = sol.routes_0.get(k).type;
					boolean CD_to_PV = false;
					if(r_type > 0){
						if(sol.routes_0.get(k).load + all_customers.get(cus1.id).d() > Parameters.capacity[1]) {
							CD_to_PV = true;
						}
					}
					double time = 0.0;
					Point p = sol.routes_0.get(k).start;
					double cost = insert_cost(p.prev, cus1, p);
					cost = dcm_cost(cost,CD_to_PV,all_customers.get(cus1.id).d(),all_customers.get(cus1.id).geo);

					if(cost < best_improvement) {
						//check if feasible 
						if(feasible_insert(cus1, time, p.prev, p)) {
							best_improvement = cost;
							ins = p;
							before = true;
							route = k;
							inserted = true;
						}	
					}
					while(p.next != null) {
						time = time_to_p(time, p.prev, p);
						time += all_customers.get(p.id).time_at_node();
						cost = insert_cost(p, cus1, p.next);
						cost = dcm_cost(cost,CD_to_PV,all_customers.get(cus1.id).d(),all_customers.get(cus1.id).geo);
						if(cost < best_improvement) {
							//check if feasible 
							if(feasible_insert(cus1, time, p, p.next)) {
								best_improvement = cost;
								ins = p.next;
								before = true;
								route = k;
								inserted = true;
							}
						}
						p = p.next;
					}

					cost = insert_cost(p, cus1, p.next);
					
					cost = dcm_cost(cost,CD_to_PV,all_customers.get(cus1.id).d(),all_customers.get(cus1.id).geo);
					
					if(cost < best_improvement) {
						//check if feasible 
						time = time_to_p(time, p.prev, p);
						time += all_customers.get(p.id).time_at_node();
						if(feasible_insert(cus1, time, p, p.next)) {
							best_improvement = cost;
							ins = p;
							before = false;
							route = k;
							inserted = true;
						}	
					}
				}
				//insert at ins
				if(inserted) {
					
					if(before) {
						
						if(ins != null) {
							if(ins.prev!=null) {
								cus1.next = ins;
								cus1.prev = ins.prev;
								ins.prev.next=cus1;
								ins.prev=cus1;
								sol.routes_0.get(route).load += all_customers.get(cus1.id).d();
							}else {
								ins.prev=cus1;
								cus1.next = ins;
								cus1.prev = null;
								sol.routes_0.get(route).load += all_customers.get(cus1.id).d();
								sol.routes_0.get(route).start = cus1;
							}
						}
					}else {
						if(ins != null) {
							if(ins.next!=null) {
								cus1.prev = ins;
								cus1.next = ins.next;
								ins.next.prev=cus1;
								ins.next=cus1;
								sol.routes_0.get(route).load += all_customers.get(cus1.id).d();
							}else {
								ins.next=cus1;
								cus1.next = null;
								cus1.prev = ins;
								sol.routes_0.get(route).load += all_customers.get(cus1.id).d();
							}
						}	
					}
				}else {
					sol.routes_0.add(new Route(cus1));
				}
			}
			
		}
		private void random_insert_with_noise(Solution sol) {
			while(removed.size() > 0) {
				//insert
				Point cus1 = sol.cus[removed.get(removed.size()-1)];//customer going to be inserted
				removed.remove(removed.size()-1);// remove customer from the list 
				Point ins = null; // best customer where it is going to be inserted
				int route = 0;
				boolean inserted = false;
				boolean before = false; // before or after customer
				double best_improvement = Double.MAX_VALUE; // best insertion value so far
				
				for(int k = 0; k < sol.routes_0.size(); k ++) {
					if(sol.routes_0.get(k).load + all_customers.get(cus1.id).d() > Parameters.capacity[0]) {
						continue;
					}
					int r_type = sol.routes_0.get(k).type;
					boolean CD_to_PV = false;
					if(r_type > 0){
						if(sol.routes_0.get(k).load + all_customers.get(cus1.id).d() > Parameters.capacity[1]) {
							CD_to_PV =true;
						}
					}
					
					
					double time = 0.0;
					Point p = sol.routes_0.get(k).start;
					double cost = insert_cost(p.prev, cus1, p);
					cost = s_cost(cost, CD_to_PV, sol.routes_0.get(k).preference,sol.routes_0.get(k).distance);
					if(noise(cost,best_improvement)) {
						//check if feasible 
						if(feasible_insert(cus1, time, p.prev, p)) {
							best_improvement = cost;
							ins = p;
							before = true;
							route = k;
							inserted = true;
						}	
					}
					
					while(p.next != null) {
						time = time_to_p(time, p.prev, p);
						time += all_customers.get(p.id).time_at_node();
						cost = insert_cost(p, cus1, p.next);
						cost = s_cost(cost, CD_to_PV, sol.routes_0.get(k).preference,sol.routes_0.get(k).distance);
						if(noise(cost, best_improvement)) {
							//check if feasible 
							if(feasible_insert(cus1, time, p, p.next)) {
								best_improvement = cost;
								ins = p.next;
								before = true;
								route = k;
								inserted = true;
							}
						}
						p = p.next;
					}
					cost = insert_cost(p, cus1, p.next);
					cost = s_cost(cost, CD_to_PV, sol.routes_0.get(k).preference,sol.routes_0.get(k).distance);
					if(noise(cost, best_improvement)) {
						//check if feasible 
						time = time_to_p(time, p.prev, p);
						time += all_customers.get(p.id).time_at_node();
						if(feasible_insert(cus1, time, p, p.next)) {
							best_improvement = cost;
							ins = p;
							before = false;
							route = k;
							inserted = true;
						}	
					}
				}
				//insert at ins
				if(inserted) {
					
					if(before) {
						
						if(ins != null) {
							if(ins.prev!=null) {
								cus1.next = ins;
								cus1.prev = ins.prev;
								ins.prev.next=cus1;
								ins.prev=cus1;
								sol.routes_0.get(route).load += all_customers.get(cus1.id).d();
								
							}else {
								
								ins.prev=cus1;
								cus1.next = ins;
								cus1.prev = null;
						
								sol.routes_0.get(route).load += all_customers.get(cus1.id).d();
								sol.routes_0.get(route).start = cus1;
							}
						}
					}else {
						
						if(ins != null) {
							if(ins.next!=null) {
								cus1.prev = ins;
								cus1.next = ins.next;
								ins.next.prev=cus1;
								ins.next=cus1;
								sol.routes_0.get(route).load += all_customers.get(cus1.id).d();
							}else {
								ins.next=cus1;
								cus1.next = null;
								cus1.prev = ins;
								sol.routes_0.get(route).load += all_customers.get(cus1.id).d();
							}
						}	
					}
				}else {
					sol.routes_0.add(new Route(cus1));
				}
			}
			
		}
		private void insert_CD(Solution sol) {
			while(removed.size() > 0) {
				//insert
				Point cus1 = sol.cus[removed.get(removed.size()-1)];//customer going to be inserted
				removed.remove(removed.size()-1);// remove customer from the list 
				Point ins = null; // best customer where it is going to be inserted
				int route = 0;
				boolean inserted = false;
				boolean before = false; // before or after customer
				double best_improvement = Double.MAX_VALUE; // best insertion value so far
				for(int o = 0; o < 2; o++) {//cd first then pv
					if(inserted) {break;}
				for(int k = 0; k < sol.routes_0.size(); k ++) {
					if(sol.routes_0.get(k).load + all_customers.get(cus1.id).d() > Parameters.capacity[0]) {
						continue;
					}
					int r_type = sol.routes_0.get(k).type;
					if(r_type==0) {
						if(o<1) {
							continue;
						}
					}else if(o>0){
							continue;
					}
					boolean CD_to_PV = false;
					if(r_type > 0){
						if(sol.routes_0.get(k).load + all_customers.get(cus1.id).d() > Parameters.capacity[1]) {
							CD_to_PV =true;
						}
					}
					
					
					double time = 0.0;
					Point p = sol.routes_0.get(k).start;
					double cost = insert_cost(p.prev, cus1, p);
					cost = s_cost(cost, CD_to_PV, sol.routes_0.get(k).preference,sol.routes_0.get(k).distance);
					if(cost < best_improvement) {
						//check if feasible 
						if(feasible_insert(cus1, time, p.prev, p)) {
							best_improvement = cost;
							ins = p;
							before = true;
							route = k;
							inserted = true;
						}	
					}
					
					while(p.next != null) {
						time = time_to_p(time, p.prev, p);
						time += all_customers.get(p.id).time_at_node();
						cost = insert_cost(p, cus1, p.next);
						cost = s_cost(cost, CD_to_PV, sol.routes_0.get(k).preference,sol.routes_0.get(k).distance);
						if(cost < best_improvement) {
							//check if feasible 
							if(feasible_insert(cus1, time, p, p.next)) {
								best_improvement = cost;
								ins = p.next;
								before = true;
								route = k;
								inserted = true;
							}
						}
						p = p.next;
					}
					cost = insert_cost(p, cus1, p.next);
					cost = s_cost(cost, CD_to_PV, sol.routes_0.get(k).preference,sol.routes_0.get(k).distance);
					if(cost < best_improvement) {
						//check if feasible 
						time = time_to_p(time, p.prev, p);
						time += all_customers.get(p.id).time_at_node();
						if(feasible_insert(cus1, time, p, p.next)) {
							best_improvement = cost;
							ins = p;
							before = false;
							route = k;
							inserted = true;
						}	
					}
				}
				}
				//insert at ins
				if(inserted) {
					
					if(before) {
						
						if(ins != null) {
							if(ins.prev!=null) {
								cus1.next = ins;
								cus1.prev = ins.prev;
								ins.prev.next=cus1;
								ins.prev=cus1;
								sol.routes_0.get(route).load += all_customers.get(cus1.id).d();
								
							}else {
								
								ins.prev=cus1;
								cus1.next = ins;
								cus1.prev = null;
						
								sol.routes_0.get(route).load += all_customers.get(cus1.id).d();
								sol.routes_0.get(route).start = cus1;
							}
						}
					}else {
						
						if(ins != null) {
							if(ins.next!=null) {
								cus1.prev = ins;
								cus1.next = ins.next;
								ins.next.prev=cus1;
								ins.next=cus1;
								sol.routes_0.get(route).load += all_customers.get(cus1.id).d();
							}else {
								ins.next=cus1;
								cus1.next = null;
								cus1.prev = ins;
								sol.routes_0.get(route).load += all_customers.get(cus1.id).d();
							}
						}	
					}
				}else {
					sol.routes_0.add(new Route(cus1));
				}
			}
			
		}
		private void insert_PV(Solution sol) {
			while(removed.size() > 0) {
				//insert
				Point cus1 = sol.cus[removed.get(removed.size()-1)];//customer going to be inserted
				removed.remove(removed.size()-1);// remove customer from the list 
				Point ins = null; // best customer where it is going to be inserted
				int route = 0;
				boolean inserted = false;
				boolean before = false; // before or after customer
				double best_improvement = Double.MAX_VALUE; // best insertion value so far
				for(int o = 0; o < 2; o++) {//pv first then cd
					if(inserted) {break;}
				for(int k = 0; k < sol.routes_0.size(); k ++) {
					if(sol.routes_0.get(k).load + all_customers.get(cus1.id).d() > Parameters.capacity[0]) {
						continue;
					}
					int r_type = sol.routes_0.get(k).type;
					if(r_type>0) {
						if(o<1) {
							continue;
						}
					}else if(o>0){
							continue;
					}
					boolean CD_to_PV = false;
					if(r_type > 0){
						if(sol.routes_0.get(k).load + all_customers.get(cus1.id).d() > Parameters.capacity[1]) {
							CD_to_PV =true;
						}
					}
					
					
					double time = 0.0;
					Point p = sol.routes_0.get(k).start;
					double cost = insert_cost(p.prev, cus1, p);
					cost = s_cost(cost, CD_to_PV, sol.routes_0.get(k).preference,sol.routes_0.get(k).distance);
					if(cost < best_improvement) {
						//check if feasible 
						if(feasible_insert(cus1, time, p.prev, p)) {
							best_improvement = cost;
							ins = p;
							before = true;
							route = k;
							inserted = true;
						}	
					}
					
					while(p.next != null) {
						time = time_to_p(time, p.prev, p);
						time += all_customers.get(p.id).time_at_node();
						cost = insert_cost(p, cus1, p.next);
						cost = s_cost(cost, CD_to_PV, sol.routes_0.get(k).preference,sol.routes_0.get(k).distance);
						if(cost < best_improvement) {
							//check if feasible 
							if(feasible_insert(cus1, time, p, p.next)) {
								best_improvement = cost;
								ins = p.next;
								before = true;
								route = k;
								inserted = true;
							}
						}
						p = p.next;
					}
					cost = insert_cost(p, cus1, p.next);
					cost = s_cost(cost, CD_to_PV, sol.routes_0.get(k).preference, sol.routes_0.get(k).distance);
					if(cost < best_improvement) {
						//check if feasible 
						time = time_to_p(time, p.prev, p);
						time += all_customers.get(p.id).time_at_node();
						if(feasible_insert(cus1, time, p, p.next)) {
							best_improvement = cost;
							ins = p;
							before = false;
							route = k;
							inserted = true;
						}	
					}
				}
				}
				//insert at ins
				if(inserted) {
					
					if(before) {
						
						if(ins != null) {
							if(ins.prev!=null) {
								cus1.next = ins;
								cus1.prev = ins.prev;
								ins.prev.next=cus1;
								ins.prev=cus1;
								sol.routes_0.get(route).load += all_customers.get(cus1.id).d();
								
							}else {
								
								ins.prev=cus1;
								cus1.next = ins;
								cus1.prev = null;
						
								sol.routes_0.get(route).load += all_customers.get(cus1.id).d();
								sol.routes_0.get(route).start = cus1;
							}
						}
					}else {
						
						if(ins != null) {
							if(ins.next!=null) {
								cus1.prev = ins;
								cus1.next = ins.next;
								ins.next.prev=cus1;
								ins.next=cus1;
								sol.routes_0.get(route).load += all_customers.get(cus1.id).d();
							}else {
								ins.next=cus1;
								cus1.next = null;
								cus1.prev = ins;
								sol.routes_0.get(route).load += all_customers.get(cus1.id).d();
							}
						}	
					}
				}else {
					sol.routes_0.add(new Route(cus1));
				}
			}
			
		}
		private boolean accept(Solution sol) {
			
			double e = 1/Math.exp((sol.get_solution() - solution.get_solution())/Temperature);
			double r = uniform();
			if(e > r ) {
				return true;
			}else {return false;}
		}

		private double s_cost(double c, boolean cP, int pre, double d) {
			if(cP) {
				return c + f_cost(0) - f_cost(pre) + d - d*v_cost(pre);
			}else {
				return c*v_cost(pre);
			}
		}
		private double dcm_cost(double c, boolean cP, double demand, double geo) {
			if(cP) {
				return c;
			}else {
				return c*Parameters.DCMBetas[0] + demand*Parameters.DCMBetas[1] + Parameters.DCMBetas[2] + geo*Parameters.DCMBetas[3]; //distance beta, load beta, size beta, geographical beta
			}
		}
		private boolean noise(double c, double b) {
			int n = rand.nextInt(2);
			int n1 = rand.nextInt(2);
			double p = 0.105;
			if(c < b + c*p*(n-n1)) {
				return true;
			}else {
				return false;
			}
		}
		private boolean feasible_insert(Point p_in ,double t, Point p1, Point p2 ) {
			//use only for time windows
			boolean feasible = false;
			if(p1!=null ) {
				p1.next = p_in;
				p_in.next = p2;
				feasible = feasible_recursive(t, p1, p_in);
				p_in.next = null;
				p1.next = p2;	
			} else if(p1 == null) {
				p_in.next = p2;
				feasible = feasible_recursive(t, p1, p_in);
				p_in.next = null;
			}
			return feasible;
		}
		private boolean feasible_recursive(double time, Point p1, Point p2 ) {
			time = time_to_p(time, p1 ,p2);
			if(p2==null) {
				return true;
			}
			else if( time > all_customers.get(p2.id).b()) {
				return false;
			}else if(p2.next !=null){
				time += all_customers.get(p2.id).time_at_node();
				return feasible_recursive(time, p2, p2.next);
			}else {
				return true;
			}
			
		}
		
		private double insert_cost(Point p1, Point p3, Point p2) {
			if(p1==null && p2!=null) {
				return Cost[depot_start.id][p3.id] + Cost[p3.id][p2.id] - Cost[depot_start.id][p2.id];
			}else if(p1!=null && p2!=null ){
				return Cost[p1.id][p3.id] + Cost[p3.id][p2.id] - Cost[p1.id][p2.id];
			}else if(p1!=null && p2==null) {
				return Cost[p1.id][p3.id] + Cost[p3.id][depot_start.id] - Cost[p1.id][depot_start.id];
			}else {
				return Cost[depot_start.id][p3.id] + Cost[p3.id][depot_start.id];
			}	
		}
	}
	private class Point{
		public int id;
		public Point prev;
		public Point next;
		public Point(int i){
			id = i;
			prev = null;
			next = null;
		}
		public Point (Point po) {
			this.id = po.id;
			this.prev = po.prev;
			this.next = po.next;
		}
	}
	public class Solution{
		public double s_value = Double.MAX_VALUE;
		public List<Route> routes_0 = new ArrayList<>();
		Point [] cus;
		public Solution(Solution sol) {
			this.cus = new Point [num_customers];
			this.s_value = sol.s_value;
			for(int k = 0 ; k < sol.routes_0.size(); k++) {
				Route r = new Route(sol.routes_0.get(k));
				this.routes_0.add(r);
				Point p = r.start;
				this.cus[p.id] = r.start;
				while(p.next!=null) {
					Point p2 = new Point(p.next);
					p.next = p2;
					p2.prev = p;
					p=p2;
					this.cus[p.id] = p;
				}
			}	
		}
		public Solution(double v ,List<Route> routes_1){
			this.cus = new Point [num_customers];
			this.s_value = v;
			this.routes_0 = routes_1;
			for(int k = 0 ; k < routes_1.size(); k++) {
				Point p = routes_0.get(k).start;
				this.cus[p.id] = p;
				while(p.next!=null) {
					p=p.next;
					this.cus[p.id] = p;
				}
			}
			update_vehicle(1);
			get_solution();
		}public int route_type(double l) {
			if(l<Parameters.capacity[1]) {
				return 1;
			}else {
				return 0;
			}
		}
		
		public void update_vehicle(int b) {
			s_value = 0.0;
			int cD = 0;
			Collections.sort(this.routes_0);
			for(int k = 0; k < routes_0.size(); k ++) {
				routes_0.get(k).size();
				routes_0.get(k).type = route_type(routes_0.get(k).load);
				if(routes_0.get(k).type == 1 && (cD < Parameters.CD_max)) {
					cD +=1;
					routes_0.get(k).cost = routes_0.get(k).distance*v_cost(cD);
					routes_0.get(k).cost += f_cost(cD);
					routes_0.get(k).preference = cD;
				}else {
					routes_0.get(k).cost = routes_0.get(k).distance*v_cost(0);
					routes_0.get(k).cost += f_cost(0);
					routes_0.get(k).type = 0;
					routes_0.get(k).preference = 0; // professional: clear any crowd rank left from an earlier evaluation
				}
				s_value+=routes_0.get(k).cost;
			} 
		}

		// Prices crowd routes with RoutePricing (logit acceptance + golden-section search).
		// Implemented but NOT used by the search: the LNS objective is update_vehicle.
		public void price_routes(int b) {
			s_value = 0.0;
			int cD = 0;
			for(int k = 0; k < routes_0.size(); k ++) {
				routes_0.get(k).size();// organizes the route k
				routes_0.get(k).type = route_type(routes_0.get(k).load);
				if(routes_0.get(k).type == 1 && (cD < Parameters.CD_max || b < 1)) {
					cD +=1;
					RoutePricing.Offer offer = RoutePricing.price(routes_0.get(k).U_notprice, routes_0.get(k).distance);
					routes_0.get(k).offered_utility = offer.utility;
					routes_0.get(k).cost = offer.cost;
				}else {
					routes_0.get(k).cost = routes_0.get(k).distance*v_cost(0);
					routes_0.get(k).cost += f_cost(0);
					routes_0.get(k).type = 0;
					routes_0.get(k).preference = 0; // professional: clear any crowd rank left from an earlier evaluation
				}
				s_value+=routes_0.get(k).cost;
			} 
		}

		public double get_solution() {
			double cost = 0.0;
			for (int k = 0; k < routes_0.size(); k ++) {
				cost += routes_0.get(k).cost;
			}
			s_value = cost;
			return s_value;
		}
		public int get_vehicles() {
			return routes_0.size();
		}
		public int get_cd() {
			int vehi = 0;
			for(int k = 0; k<routes_0.size(); k++) {
				if(routes_0.get(k).type>0) {	
					vehi+=1;
				}
			}
			return vehi;
		}
	}
	private class Route implements Comparable<Route>{
		public Point start = null;
		public int preference=0;
		public int type;
		public double load;//total load of the route
		public double cost;// the total cost based on the distance. 
		public double U_notprice; // the utility of the route based on the discrete choice model.
		public double offered_utility; // set by price_routes (pricing not used by the search)
		public double geo;// The total value of the geographical location of customers. 
		public double distance; // Total distance of routes. 
		public int size; // Total number of customers. 
		public Point end = null;
		public Route(Point p) {
			type = 0;
			start = p;
			load = all_customers.get(p.id).d();
			size();
		}
		public Route(Route r) {
			this.load = r.load;
			this.cost = r.cost;
			this.size = r.size;
			this.geo = r.geo;
			this.type = r.type;
			this.U_notprice = r.U_notprice;
			this.preference = r.preference;
			this.distance = r.distance;
			start = new Point(r.start);
			size();
		}
		public int size() {
			Point p = start;
			double demand = all_customers.get(p.id).d();
			int s = 1;
			double g = all_customers.get(p.id).geo;
			this.distance = Cost[depot_start.id][p.id];
			while(p.next!= null) {
				this.distance += Cost[p.id][p.next.id];
				s+=1;
				demand += all_customers.get(p.next.id).d();
				p = p.next;
				g += all_customers.get(p.id).geo;
			}
			this.distance += Cost[p.id][depot_start.id]; //return to depot.
			end = p;
			load = demand;
			size = s;
			geo = g;
			U_notprice = distance*Parameters.DCMBetas[0] + load*Parameters.DCMBetas[1] + size*Parameters.DCMBetas[2] + geo*Parameters.DCMBetas[3];
			return s;
		}

		public double getDistance() {
			return this.distance;
		}

		// Checks capacity, every customer's time window, and the return to the depot
		// before its due time (read from the instance). Read-only: unlike before, it does
		// not change this route's load, distance or end.
		public boolean feasible() {
			int first = start.id;
			double time = Math.max(Cost[depot_start.id][first], all_customers.get(first).a());
			double route_load = all_customers.get(first).d();
			double capacity = Parameters.capacity[this.type];
			if(route_load > capacity || time > all_customers.get(first).b()) {
				return false;
			}
			time += all_customers.get(first).time_at_node();
			Point p = start;
			while(p.next != null) {
				time += Cost[p.id][p.next.id];
				route_load += all_customers.get(p.next.id).d();
				if(route_load > capacity || time > all_customers.get(p.next.id).b()) {
					return false;
				}
				time = Math.max(time, all_customers.get(p.next.id).a())
					+ all_customers.get(p.next.id).time_at_node();
				p = p.next;
			}
			time += Cost[p.id][depot_start.id];
			return time <= depot_due_time;
		}
		@Override
	    public int compareTo(Route member) {
	        if (this.distance == member.getDistance()) {
	            return 0;
	        } else if (this.distance > member.getDistance()) {
	            return -1;
	        } else {
	            return 1;
	        }
	    }
	    @Override
	    public String toString() {
	        return "[ Type =" + type + ", distance =" + distance + ", preference" + preference + "]";
	    }
	}
	private class Node {
		public int id;
		public int id_external;
		private double xcoord;
		private double ycoord;
		private double t_at_node;
		
		public Node(int external_id, double x, double y, double t) {
			this.id = all_nodes.size();
			this.id_external = external_id;
			this.xcoord = x;
			this.ycoord = y;
			this.t_at_node = t;
			all_nodes.put(this.id, this);
		}
		public double time_to_node(Node node_to) {
			return Math.sqrt(Math.pow(this.xcoord-node_to.xcoord, 2)+Math.pow(this.ycoord-node_to.ycoord, 2));
		}
		public double time_at_node() {
			return t_at_node;
		}
		public double getxcoord() {
			return this.xcoord;
		}
		public double getycoord() {
			return this.ycoord;
		}
	}
	private class Depot extends Node {
		public Depot(int external_id, double x, double y) {
			super(external_id,x,y,0);
		}
	}
	private class Customer extends Node{
		private double demand;
		private double ready_time;
		private double due_date;
		private double geo;
		public Customer(int external_id, double x, double y, double demand, double ready_time, double due_date, double service_time) {
			super(external_id,x,y,service_time);
			this.demand = demand;
			this.ready_time = ready_time;
			this.due_date = due_date;
			all_customers.put(this.id, this);
		}
		public void set_geo(double g) {
			this.geo = g;
		}
		public double a() {
			return ready_time;
		}
		public double b() {
			return due_date;
		}
		public double d() {
			return demand;
		}
		
	}
	// Clarke-Wright savings heuristic with time-window and capacity checks: the starting solution.
	public class ClarkeWright {
		public Solution begin() {
			boolean merged =  true;
			List<Route> routes1 = new ArrayList<>();
			for(int i = 0; i < num_customers; i ++) {
				Point p = new Point(i);
				routes1.add(new Route(p));
			}
			while (merged) {
				merged = false;
				int y = 0;
				int t = 0;
				double best = 0.0;
				for(int k = 0; k < routes1.size(); k ++) {
					Point p = routes1.get(k).start;
					int demand = 0;
					double ti = Cost[depot_start.id][p.id];
			
					while(p.next != null) {
						ti = Math.max(all_customers.get(p.id).a(), ti);
						demand += (int)all_customers.get(p.id).d();
						ti += all_customers.get(p.id).time_at_node();
						ti += Cost[p.id][p.next.id];
						p = p.next;
					}
					ti = Math.max(all_customers.get(p.id).a(), ti);
					demand += (int)all_customers.get(p.id).d();
					ti += all_customers.get(p.id).time_at_node();
					
					for(int k2 = 0 ; k2 < routes1.size(); k2++) {
						if(k2==k) {continue;}
						Point p2 = routes1.get(k2).start;
						double cs = cost_savings(p.id, p2.id);
						
						if(cost_savings(p.id, p2.id) > best + 0.00001){
							// check if feasible
							boolean feasible = true;
							int demand2 = demand;
							double ti2 = ti + Cost[p.id][p2.id];
							while(p2.next != null && feasible){
								ti2 = Math.max(all_customers.get(p2.id).a(), ti2);
								demand2 += (int)all_customers.get(p2.id).d();
								if(ti2 > all_customers.get(p2.id).b()) {
									feasible = false;
								}
								if(demand2 > Parameters.capacity[0]) {
									feasible = false;
								}
								ti2 += all_customers.get(p2.id).time_at_node();
								ti2 += Cost[p2.id][p2.next.id];
								p2 = p2.next;
							}
							ti2 = Math.max(all_customers.get(p2.id).a(), ti2);
							demand2 += (int)all_customers.get(p2.id).d();
							if(ti2 > all_customers.get(p2.id).b()) {
								feasible = false;
							}
							if(demand2 > Parameters.capacity[0]) {
								feasible = false;
							}
							if(feasible) {
								y = k;
								t = k2;
								best = cs;
								merged = true;
							}
						}
					}
				}
				if(merged) {
					merge(routes1.get(y).start,routes1.get(t).start);
					Route r = new Route(routes1.get(y).start);
					r.load = routes1.get(y).load + routes1.get(t).load;
					routes1.add(r);
					if(y>t) {
						routes1.remove(y);
						routes1.remove(t);
					}else {
						routes1.remove(t);
						routes1.remove(y);
					}
				}
			}
			return new Solution(0.0,routes1);	
		}
		private void merge(Point start, Point end) {
			Point p = start;
			while(p.next !=null) {
				p = p.next;
			}
			p.next = end;
			end.prev = p;
		}
		private double cost_savings(int i , int j) {
			return (Cost[depot_start.id][i] + Cost[depot_start.id][j]- Cost[i][j]);
		}
	}
	private double time_to_p(double t, Point p1 , Point p2) {
		if(p1==null && p2 !=null) {
			return Math.max(t + Cost[depot_start.id][p2.id], all_customers.get(p2.id).a());
		}else if(p1!=null && p2!=null){
			return Math.max(t + Cost[p1.id][p2.id], all_customers.get(p2.id).a());
		}else if(p1!=null && p2 == null) {
			return t + Cost[p1.id][depot_start.id];
		}else {
			return Double.MAX_VALUE;
		}	
	}
	public void create_clusters() {
		List<int[]> clusters = new ArrayList<int[]>();
		int [] cluster_1 = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10 ,11, 75};
		clusters.add(cluster_1);
		int [] cluster_2= {12, 13, 14, 15, 16, 17, 18, 19};
		clusters.add(cluster_2);
		int [] cluster_3 = {20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30};
		clusters.add(cluster_3);
		int [] cluster_4 = {31, 32, 33, 34, 35, 36, 37, 38, 39};
		clusters.add(cluster_4);
		int [] cluster_5 = {40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52};
		clusters.add(cluster_5);
		int [] cluster_6 = {53, 54, 55, 56, 57, 58, 59, 60};
		clusters.add(cluster_6);
		int [] cluster_7 = {61, 62, 63, 64, 65, 66, 67, 68, 69, 72, 74};
		clusters.add(cluster_7);
		int [] cluster_8 = {70, 71, 73, 76, 77, 78, 79, 80, 81};
		clusters.add(cluster_8);
		int [] cluster_9 = {82, 83, 84, 85, 86, 87, 88, 89, 90, 91};
		clusters.add(cluster_9);
		int [] cluster_10 = {92, 93, 94, 95, 96, 97, 98, 99, 100};
		clusters.add(cluster_10);
		for(int i = 0; i < clusters.size(); i ++) {
			List<Integer> temp = new ArrayList<>();
			boolean atleastone = false;
			for(int j = 0; j < clusters.get(i).length; j++) {
				if(clusters.get(i)[j] <= Parameters.num_customers) {
					atleastone = true;
					int customer_internal = clusters.get(i)[j]-1;
					temp.add(customer_internal);
				}
			}
			if(atleastone) {
				new Cluster(temp);
			}
		}
	}
	// Prints each route of the best solution with its vehicle type, load, distance and whether it
	// passes Route.feasible() (capacity, time windows, depot due time). Customer numbers are the
	// instance's own (CUST NO. column).
	public void print_routes() {
		for(int r = 0; r < incumbent.routes_0.size(); r++) {
			Route route = incumbent.routes_0.get(r);
			StringBuilder line = new StringBuilder();
			line.append(String.format("  route %d: %-12s load %5.1f  distance %8.2f  %-10s depot ->",
				r + 1, route.type > 0 ? "crowd" : "professional", route.load, route.distance,
				route.feasible() ? "feasible" : "INFEASIBLE"));
			for(Point p = route.start; p != null; p = p.next) {
				line.append(' ').append(all_customers.get(p.id).id_external);
			}
			System.out.println(line.append(" -> depot"));
		}
	}
	// Runs the LNS for a fixed number of iterations; the best solution is kept in incumbent.
	public void solve() {
		LnsSearch problem = new LnsSearch();
		problem.iterations = 700000;
		for(int i = 0 ; i < problem.iterations; i++) {
			problem.iterate();
		}
	}

	private void fill_cost() {
		Cost = new double [num_customers +1][num_customers +1];
		//fill cost matrix with the distance between customers.
		for (int i : all_nodes.keySet()) {
			for (int j : all_nodes.keySet()) {
				Cost[i][j] = all_nodes.get(i).time_to_node(all_nodes.get(j));
			}
		}
	}
	private void ReadData() {
		// Instances are read from the "instances" folder next to where the program is run.
		// Override with -Dinstances.dir=<folder>.
		String ins_path = System.getProperty("instances.dir", "instances") + File.separator + instance + ".txt";
		File file = new File(ins_path);
		if(!file.isFile()) {
			throw new IllegalArgumentException("Instance file not found: " + file.getAbsolutePath()
				+ " (instances are read from the folder set by -Dinstances.dir, default \"instances\")");
		}
		boolean depot_read = false;
		boolean capacity_read = false;
		try {
			int id_external = 0;
			double xcoord = 0;
			double ycoord = 0;
			double demand = 0;
			double readyt = 0;
			double duedate = 0;
			double servicet = 0;
			BufferedReader br = new BufferedReader(new FileReader(file));
		 	double xc = 0;
		 	double yc = 0;
		  	String st;
		  	int count = 0;
		  	while ((st = br.readLine()) != null){
		    	count += 1;
		    	if(count == 5) {
		    		// Solomon line 5: "<number of vehicles> <vehicle capacity>". The file's capacity is the
		    		// professional vehicle capacity; crowd capacities stay as set in Parameters.
		    		String[] fields = st.trim().split("\s+");
		    		if(fields.length >= 2) {
		    			try {
		    				Parameters.capacity[0] = Double.parseDouble(fields[1]);
		    				capacity_read = true;
		    			} catch (NumberFormatException e) {
		    				// reported below
		    			}
		    		}
		    	}
		    	if(count > 9 && this.num_customers  + 11 > count){
					var x = "";
					int word = 0;
					for (int i = 0; i < st.length();i++){
						char c = st.charAt(i);
			    		if(Character.isDigit(c)){
			    			x+= c; 
						}if(!Character.isDigit(c)|| i == st.length() -1){
							if(x.length()>0 ){
								if(word == 0){id_external = Integer.parseInt(x);}
								if(word == 1){xcoord = Double.parseDouble(x);}
								if(word == 2){ycoord = Double.parseDouble(x);}
								if(word == 3){demand = Double.parseDouble(x);}
								if(word == 4){readyt = Double.parseDouble(x);}
								if(word == 5){duedate = Double.parseDouble(x);}
								if(word == 6){servicet = Double.parseDouble(x);}
								word+=1;x="";
							}
						}	
					}
					if(count == 10) {
						xc = xcoord;
						yc = ycoord;
						depot_due_time = duedate;
						depot_read = true;
							
					}else {
						new Customer(id_external, xcoord, ycoord, demand, readyt, duedate, servicet);
					}
				}
		  	}
		  	br.close();
		    if(!capacity_read) {
		    	throw new IllegalArgumentException("Instance file " + file.getAbsolutePath()
		    		+ " has no vehicle capacity on line 5: is it a Solomon-format file?");
		    }
		    if(!depot_read) {
		    	throw new IllegalArgumentException("Instance file " + file.getAbsolutePath()
		    		+ " has no depot line (line 10): is it a Solomon-format file?");
		    }
		    if(all_customers.size() < this.num_customers) {
		    	throw new IllegalArgumentException("Instance file " + file.getAbsolutePath() + " has only "
		    		+ all_customers.size() + " customer lines, but " + this.num_customers + " customers were requested");
		    }
		    depot_start = new Depot(0,xc,yc);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not read instance file " + file.getAbsolutePath(), ex);
		}
	}
}