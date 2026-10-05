# LNS for the VRPTW with crowd-sourced vehicles

A large neighborhood search (LNS) with simulated annealing for the vehicle routing problem with time windows (VRPTW), in which some routes can be given to **crowd drivers** instead of professional vehicles. Crowd drivers are cheaper, but their availability is uncertain: if the driver assigned to a route does not show up, the route is served by a professional vehicle at a higher price (recourse). The search minimizes the **expected** total cost.

Plain Java, no external libraries or solvers.

## The model

- **Customers and time windows.** Solomon VRPTW instances. By default the first 25 customers of each file are used; change this with `--customers N`.
- **Professional vehicles.** The capacity is read from the instance file (200 for the C1/R1/RC1 classes, 700 or 1000 for the type-2 classes). Fixed cost 100, cost 1.0 per unit of distance.
- **Crowd drivers.** Capacity 100, fixed cost 50, cost 0.5 per unit of distance if they show up. A route whose load stays below 100 can be offered to a crowd driver. These are model constants in `Parameters.java`, independent of the instance.
- **Stochastic availability and recourse.** A pool of 100 crowd drivers is available, each one with probability 0.05. The crowd driver of rank *s* fails to appear with probability *p* = P(fewer than *s* drivers available), from a binomial distribution. In that case a professional vehicle serves the route at twice the professional price. The expected cost of a crowd route is therefore `50(1-p) + 2·100·p` plus its distance times `0.5(1-p) + 2p` (`Parameters.Cal_CD_max`).
- **How many crowd drivers.** Ranks are used only while the probability that the driver shows up stays above the break-even ratio between crowd and professional cost (2/3 with the default parameters). This allows at most 4 crowd routes.
- **Assigning routes.** After every move the routes are sorted by distance, longest first. Routes with load below the crowd capacity get crowd ranks 1, 2, … in that order; all other routes are professional (`Solution.update_vehicle`).

`Parameters.java` also defines a second crowd-driver type (capacity 50, fixed cost 25). Its recourse costs are computed, but the search does not use it.

## The algorithm

1. **Start:** a Clarke-Wright savings heuristic with time-window and capacity checks (`ClarkeWright`).
2. **Each iteration** (700,000 in total, `LnsSearch.iterate`) applies one destroy and one repair operator:
   - **Destroy** one of: remove 5–8 random customers with the default 25 customers (70%), remove two or more whole routes (10%), remove the first part of every route (10%), remove the last part of every route (10%).
   - **Repair** one of, all greedy best insertion with time-window checks: positions scored with a logit-style utility of distance, demand and location (70%), positions scored by expected cost with random noise (10%), professional routes first (10%), crowd routes first (10%).
   - The operator probabilities are fixed; there is no adaptive weight update.
3. **Acceptance:** simulated annealing. The temperature starts at 600, is multiplied by 0.99997 each iteration, and is reset to 400 once it drops below 0.003.
4. The best solution found is reported.

### Route pricing (implemented, not yet integrated)

`RoutePricing.java` contains a pricing model for crowd routes: a crowd driver accepts a route with a logit probability that depends on the utility *V* offered, and a golden-section search chooses *V* to minimize the expected cost. `Solution.price_routes` applies it to a solution. **The search does not use it yet**: the objective uses the recourse costs above.

## Build and run

Requires JDK 10 or later; tested with Temurin JDK 17. From the repository folder:

```
./run.sh            # Linux, macOS, Git Bash
run.bat             # Windows
```

The scripts use the JDK in `JAVA_HOME` if it is set; otherwise `javac` and `java` must be on the `PATH`. With no arguments they solve the included instances `c107`, `c108` and `c109`.

Options:

| Option | Effect |
|---|---|
| `c101 r101 ...` | Instances to solve, read from `instances/<name>.txt`. Use `-Dinstances.dir=<folder>` to read them from another folder. |
| `--customers N` | Use the first N customers of each instance (default 25, at least 5). Also `-Dcustomers=N`. |
| `--routes` | Print the final routes, each checked for capacity, time windows and the depot due time. |
| `SEED=42` | Environment variable for a reproducible run (`SEED=42 ./run.sh`, or `set SEED=42` before `run.bat`). Without it the run is unseeded. |

Examples:

```
SEED=42 ./run.sh --routes c107
./run.sh --customers 50 c108
```

To build and run by hand:

```
javac -d build *.java
java -Dseed=42 -cp build App c107
```

A missing or malformed instance file stops the program with a message naming the file, and exit status 1.

## Example output

```
$ SEED=42 ./run.sh --routes c107
c107
 Iter  Solution  Vehi  CD  Time 
Reheating happens 400.0
0 441.56758083731586 3 1 2.046999931335449
  route 1: professional load 200.0  distance    99.96  feasible   depot -> 13 17 18 19 15 16 14 12 4 -> depot
  route 2: professional load 170.0  distance    72.19  feasible   depot -> 5 3 7 8 10 11 9 6 2 1 21 -> depot
  route 3: crowd        load  90.0  distance    36.41  feasible   depot -> 20 24 25 23 22 -> depot
```

The result line gives: run index, expected total cost, number of vehicles, number of crowd vehicles, and run time in seconds. Customer numbers in the route lines are the instance's own.

Results with the default settings (25 customers, Temurin JDK 17, about 2 seconds per instance), identical with seeds 42 and 7:

| Instance | Expected cost | Vehicles | Crowd vehicles |
|---|---|---|---|
| c107 | 441.568 | 3 | 1 |
| c108 | 441.360 | 3 | 1 |
| c109 | 438.029 | 3 | 1 |

## Known limitations

- The depot due time is checked by `--routes`, but the insertion feasibility check during the search only checks customer time windows. On the included instances all final routes respect the depot due time.
- The customer clusters behind the location term of the insertion utility are hardcoded for the Solomon customer numbering 1–100; with 25 customers that term is a constant.
- Instance lines are parsed as whole numbers, which matches the Solomon files.

## Files

| File | Contents |
|---|---|
| `App.java` | Command line: options, instance loop, output |
| `LNS.java` | Instance reading, Clarke-Wright start, destroy and repair operators, simulated annealing, solution and route classes |
| `Parameters.java` | Capacities, costs, crowd-driver pool, recourse costs |
| `Probability.java` | Binomial cumulative probability |
| `RoutePricing.java` | Route pricing with golden-section search (not yet used by the search) |
| `instances/` | Solomon instances c107, c108, c109 |

## Instances

`instances/` contains `c107`, `c108` and `c109` from the Solomon (1987) VRPTW benchmark, in the standard Solomon text format, kept byte for byte. The full benchmark is available from the SINTEF VRPTW pages: https://www.sintef.no/projectweb/top/vrptw/solomon-benchmark/
