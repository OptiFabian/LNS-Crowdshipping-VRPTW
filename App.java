import java.util.ArrayList;
import java.util.List;

public class App {
	// Usage: java App [--customers N] [--routes] [instance ...]   e.g. java App --customers 50 c107 r101
	// With no instance names, runs the default set c107, c108, c109.
	// --customers N (or -Dcustomers=N) uses the first N customers of each instance; default 25.
	// --routes prints the final routes and checks each one (capacity, time windows, depot due time).
	// Instance files are read from instances/<name>.txt (see LNS.ReadData).
	private static final String[] DEFAULT_INSTANCES = {"c107", "c108", "c109"};

	public static void main(String[] args) {
		try {
			solveAll(args);
		} catch (IllegalArgumentException | java.io.UncheckedIOException e) {
			// Bad input (for example a missing instance file): report it clearly and stop.
			System.err.println("Error: " + e.getMessage());
			System.exit(1);
		}
	}

	private static void solveAll(String[] args) {
		int customers = Integer.getInteger("customers", Parameters.DEFAULT_CUSTOMERS);
		boolean printRoutes = false;
		List<String> instances = new ArrayList<>();
		for(int i = 0; i < args.length; i++) {
			if(args[i].equals("--customers")) {
				if(i + 1 >= args.length) {
					throw new IllegalArgumentException("--customers needs a number, e.g. --customers 50");
				}
				customers = parseCustomers(args[++i]);
			} else if(args[i].startsWith("--customers=")) {
				customers = parseCustomers(args[i].substring("--customers=".length()));
			} else if(args[i].equals("--routes")) {
				printRoutes = true;
			} else {
				instances.add(args[i]);
			}
		}
		if(customers < 5) {
			// The destroy step removes at least 5 customers per iteration.
			throw new IllegalArgumentException("--customers must be at least 5, got " + customers);
		}
		Parameters.num_customers = customers;
		if(instances.isEmpty()) {
			instances = List.of(DEFAULT_INSTANCES);
		}
		for(String name : instances) {
			run(name, printRoutes);
		}
	}

	private static int parseCustomers(String value) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("--customers needs a whole number, got \"" + value + "\"");
		}
	}

	// Solves one instance and prints: run index, objective, number of vehicles, number of crowd vehicles, seconds.
	private static void run(String instance, boolean printRoutes) {
		System.out.println(instance);
		System.out.println(" Iter " + " Solution " + " Vehi " + " CD " + " Time ");
		double time = (double)System.currentTimeMillis()/1000.0;
		LNS problem = new LNS(instance);
		problem.solve();
		time = (double)System.currentTimeMillis()/1000.0 - time;
		System.out.println(0 + " " + problem.incumbent.get_solution() + " " + problem.incumbent.get_vehicles() + " " +problem.incumbent.get_cd() +" " + time);
		if(printRoutes) {
			problem.print_routes();
		}
	}
}
