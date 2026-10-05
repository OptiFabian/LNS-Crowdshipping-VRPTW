/**
 * Route pricing for crowd drivers: chooses the utility V offered to a crowd driver for a route.
 *
 * NOTE: implemented but NOT yet integrated into the search. The LNS objective uses the
 * recourse costs in Parameters (Solution.update_vehicle); only Solution.price_routes calls this.
 *
 * Model: a crowd driver accepts a route with logit probability sigma(V) = e^V / (e^V + 1).
 * The search minimizes, over V in [0, 50],
 *     f(V) = sigma(V) * (V + U) / beta  +  (1 - sigma(V)) * distance
 * where U is the route's utility without price (Route.U_notprice) and beta is the compensation
 * coefficient Parameters.DCMBetas[4]. The returned cost is sigma(V) * (V + U) / beta, i.e. the
 * expected payment to the crowd driver (the (1 - sigma) * distance term is not included).
 */
public final class RoutePricing {

	private RoutePricing() {
	}

	/** Result of pricing one route. */
	public static final class Offer {
		public final double utility; // offered utility V
		public final double cost;    // expected payment: sigma(V) * (V + U) / beta

		Offer(double utility, double cost) {
			this.utility = utility;
			this.cost = cost;
		}
	}

	/** Prices one crowd route given its utility without price and its distance. */
	public static Offer price(double utilityWithoutPrice, double distance) {
		double beta = Parameters.DCMBetas[4];
		GoldenSection search = new GoldenSection(1 / beta, utilityWithoutPrice / beta, distance);
		double v = search.minimize(0, 50);
		double accept = Math.exp(v) / (Math.exp(v) + 1);
		return new Offer(v, accept * (v + utilityWithoutPrice) / beta);
	}

	/**
	 * Golden-section search for the minimum of
	 *     f(x) = a*x*sigma(x) + b*sigma(x) - c*sigma(x) + c,   sigma(x) = e^x / (e^x + 1),
	 * on an interval (formerly the class Goldensection).
	 */
	public static final class GoldenSection {
		public static final double TOLERANCE = 1e-5;
		private static final double R = (Math.sqrt(5) - 1) / 2; // golden ratio conjugate
		private final double a, b, c;

		public GoldenSection(double a, double b, double c) {
			this.a = a;
			this.b = b;
			this.c = c;
		}

		public double f(double x) {
			double sigma = Math.exp(x) / (Math.exp(x) + 1);
			return a * x * sigma + b * sigma - c * sigma + c;
		}

		/** Returns the approximate minimizer of f on [lo, hi]. */
		public double minimize(double lo, double hi) {
			double x1 = hi - R * (hi - lo);
			double x2 = lo + R * (hi - lo);
			while (Math.abs(hi - lo) > TOLERANCE) {
				if (f(x1) > f(x2)) {
					lo = x1;
					x1 = x2;
					x2 = lo + R * (hi - lo);
				} else {
					hi = x2;
					x2 = x1;
					x1 = hi - R * (hi - lo);
				}
			}
			return (lo + hi) / 2;
		}
	}
}
