// Model parameters: vehicle capacities and costs, crowd-driver pool and availability, and the
// coefficients of the logit-style insertion utility.
public class Parameters {
	public static boolean stochastic = true;
	// Vehicle capacities: {professional, crowd type 1, crowd type 2}.
	// capacity[0] is overwritten with the capacity read from each instance file (Solomon line 5).
	// The crowd capacities are model constants, independent of the instance: a route whose load
	// stays below capacity[1] can be offered to a crowd driver.
	public static double[] capacity = {200, 100, 50};
	public static double[] alpha_beta = {2.0, 0.5,0.25};
	public static double[] f_cost = {100, 50, 25};
	public static double[] DCMBetas = {0.8, 0.2, 0.2, 0.2, 1}; // distance, load, size , g, and compensation. 
	// Number of customers read from each instance (the first N after the depot). Default 25;
	// set with --customers N on the command line or -Dcustomers=N.
	public static final int DEFAULT_CUSTOMERS = 25;
	public static int num_customers = DEFAULT_CUSTOMERS;
	public static int pool = 100;
	public static int pool_2 = 100;
	public static double p_bi = 0.05;
	public static double p_bi_2 = 0.05;
	public static int CD_max = pool;
	public static int CD_max_2 = pool_2;
	public static double [] recourse_Fcost_1;
	public static double [] recourse_Vcost_1;
	public static double [] recourse_Fcost_2;
	public static double [] recourse_Vcost_2;
	public static double [] recourse_prob;   // failure probability per rank, crowd type 1
	public static double [] recourse_prob_2; // failure probability per rank, crowd type 2
	public static void Cal_CD_max() {
	
		if(stochastic) {
			// Start from the full pool each time, so a previous instance's value cannot leak in.
			CD_max = pool;
			CD_max_2 = pool_2;
			//(alpha -1/ alpha - beta) and  (alpha -1/ alpha - F'/F)
			double a = (alpha_beta[0] - 1)/(alpha_beta[0] - alpha_beta[1]);
			double b = (alpha_beta[0]-1)/(alpha_beta[0] - f_cost[1]/f_cost[0]);
			if(a<0) {a = 1;}
			if(b<0) {b = 1;}
			double ratio_1  = Math.min(a,b);
			ratio_1 = Math.min(ratio_1, 1);
			recourse_Fcost_1 = new double[pool + 1];
			recourse_Vcost_1 = new double[pool + 1];
			recourse_prob = new double [pool + 1];
			recourse_Fcost_1[0] = f_cost[0];
			recourse_Vcost_1[0] = 1.0;
			recourse_prob[0] = 0;
			for(int i = 1; i <= pool; i++) {
				double p = Probability.cumulative_bi(pool, i, p_bi); //probability of failure
				if((1-p) < ratio_1){CD_max = i - 1;break;
				}else {
					recourse_Fcost_1[i] = f_cost[1]*(1.0 - p) + alpha_beta[0]*f_cost[0]*p;
					recourse_Vcost_1[i] = alpha_beta[1]*(1.0 - p) + alpha_beta[0]*p;
					recourse_prob[i] = p;
				}
				
			}
		
			
			////////////////////////////////////////////////////////////////////////////////
			////////////////////////2 VEHICLE TYPES ////////////////////////////////////////
			////////////////////////////////////////////////////////////////////////////////
			////////////////////////////////////////////////////////////////////////////////

			double a_2 = (alpha_beta[0] - 1)/(alpha_beta[0] - alpha_beta[2]);
			double b_2 = (alpha_beta[0]- 1)/(alpha_beta[0] - f_cost[2]/f_cost[0]);
			if(a_2<0) {a_2 = 1;}
			if(b_2<0) {b_2 = 1;}
			double ratio_2  = Math.min(a_2,b_2);
			ratio_2 = Math.min(ratio_2, 1);
			recourse_Fcost_2 = new double[pool_2 + 1];
			recourse_Vcost_2 = new double[pool_2 + 1];
			recourse_prob_2 = new double [pool_2 + 1];
			recourse_Fcost_2[0] = f_cost[0];
			recourse_Vcost_2[0] = 1.0;
			recourse_prob_2[0] = 0;
			for(int i = 1; i <= pool_2; i++) {
				double p = Probability.cumulative_bi(pool_2, i, p_bi_2); //probability of failure
				if((1-p) < ratio_2){CD_max_2 = i - 1;break;
				}else {
					recourse_Fcost_2[i] = f_cost[2]*(1.0 - p) + alpha_beta[0]*f_cost[0]*p;
					recourse_Vcost_2[i] = alpha_beta[2]*(1.0 - p) + alpha_beta[0]*p;
					recourse_prob_2[i] = p;
				}
				
			}
		
			for(int i = CD_max_2 + 1; i <= pool_2;i++) {
				recourse_Fcost_2[i] = Double.POSITIVE_INFINITY;
				recourse_Vcost_2[i] = Double.POSITIVE_INFINITY;
				recourse_prob_2[i] = Double.POSITIVE_INFINITY;
			}
			
		}
	 
	
	}
}