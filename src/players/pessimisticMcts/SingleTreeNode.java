package players.pessimisticMcts;

import core.GameState;
import players.heuristics.ModifiedAdvancedHeuristic;
import players.heuristics.AdvancedHeuristic;
import players.heuristics.CustomHeuristic;
import players.heuristics.StateHeuristic;
import utils.ElapsedCpuTimer;
import utils.Types;
import utils.Utils;
import utils.Vector2d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;


public class SingleTreeNode {
    public pMCTSParams params;

    private SingleTreeNode parent;
    private SingleTreeNode[] children;
    private double totValue;
    private int nVisits;
    private Random m_rnd;
    private int m_depth;
    private double[] bounds = new double[]{Double.MAX_VALUE, -Double.MAX_VALUE};
    private int childIdx;
    private int fmCallsCount;

    private int num_actions;
    private Types.ACTIONS[] actions;
    private List<HashMap<Types.ACTIONS, Double>> opponentActionProbs;

    private GameState rootState;
    private StateHeuristic rootStateHeuristic;

    SingleTreeNode(pMCTSParams p, Random rnd, int num_actions, Types.ACTIONS[] actions,
                   List<HashMap<Types.ACTIONS, Double>> opponentActionProbs) {
        this(p, null, -1, rnd, num_actions, actions, opponentActionProbs, 0, null);
    }

    private SingleTreeNode(pMCTSParams p, SingleTreeNode parent, int childIdx, Random rnd, int num_actions,
                           Types.ACTIONS[] actions, List<HashMap<Types.ACTIONS, Double>> opponentActionProbs,
                           int fmCallsCount, StateHeuristic sh) {
        this.params = p;
        this.fmCallsCount = fmCallsCount;
        this.parent = parent;
        this.m_rnd = rnd;
        this.num_actions = num_actions;
        this.actions = actions;
        this.opponentActionProbs = opponentActionProbs;
        children = new SingleTreeNode[num_actions];
        totValue = 0.0;
        this.childIdx = childIdx;

        if (parent != null) {
            m_depth = parent.m_depth + 1;
            this.rootStateHeuristic = sh;
        } else
            m_depth = 0;
    }

    void setRootGameState(GameState gs) {
        this.rootState = gs;
        if (params.heuristic_method == params.CUSTOM_HEURISTIC)
            this.rootStateHeuristic = new CustomHeuristic(gs);
        else if (params.heuristic_method == params.ADVANCED_HEURISTIC)
            this.rootStateHeuristic = new AdvancedHeuristic(gs, m_rnd);
        else if (params.heuristic_method == params.MODIFIED_ADVANCED_HEURISTIC)
            this.rootStateHeuristic = new ModifiedAdvancedHeuristic(gs, m_rnd);
    }

    void mctsSearch(ElapsedCpuTimer elapsedTimer) {
        double avgTimeTaken;
        double acumTimeTaken = 0;
        long remaining;
        int numIters = 0;

        int remainingLimit = 5;
        boolean stop = false;

        while (!stop) {
            GameState state = rootState.copy();
            ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();
            SingleTreeNode selected = treePolicy(state);

            double score;
            if (selected.m_depth == params.search_depth) {
                // perform pessimistic simulation
                score = selected.pessimisticRollOut(state);
            } else {
                score = selected.rollOut(state);
            }
            backUp(selected, score);

            //Stopping condition
            if (params.stop_type == params.STOP_TIME) {
                numIters++;
                acumTimeTaken += (elapsedTimerIteration.elapsedMillis());
                avgTimeTaken = acumTimeTaken / numIters;
                remaining = elapsedTimer.remainingTimeMillis();
                stop = remaining <= 2 * avgTimeTaken || remaining <= remainingLimit;
            } else if (params.stop_type == params.STOP_ITERATIONS) {
                numIters++;
                stop = numIters >= params.num_iterations;
            } else if (params.stop_type == params.STOP_FMCALLS) {
                fmCallsCount += params.rollout_depth;
                stop = (fmCallsCount + params.rollout_depth) > params.num_fmcalls;
            }
        }
        //System.out.println(" ITERS " + numIters);
    }

    private SingleTreeNode treePolicy(GameState state) {
        SingleTreeNode cur = this;

        // we are not expanding after the depth of params.search_depth
        while (!state.isTerminal() && cur.m_depth <= params.search_depth) {
            if (cur.notFullyExpanded()) {
                return cur.expand(state);
            } else {
                cur = cur.uct(state);
            }
        }
        return cur;
    }

    private SingleTreeNode expand(GameState state) {
        int bestAction = 0;
        double bestValue = -1;

        for (int i = 0; i < children.length; i++) {
            double x = m_rnd.nextDouble();
            if (x > bestValue && children[i] == null) {
                bestAction = i;
                bestValue = x;
            }
        }
        //Roll the state
        roll(state, actions[bestAction]);

        SingleTreeNode tn = new SingleTreeNode(params, this, bestAction, this.m_rnd, num_actions,
                actions, opponentActionProbs, fmCallsCount, rootStateHeuristic);
        children[bestAction] = tn;
        return tn;
    }

    private void roll(GameState gs, Types.ACTIONS act) {
        int nPlayers = 4;
        Types.ACTIONS[] actionsAll = new Types.ACTIONS[4];
        int playerId = gs.getPlayerId() - Types.TILETYPE.AGENT0.getKey();

        for (int i = 0; i < nPlayers; ++i) {
            if (playerId == i) {
                actionsAll[i] = act;
            } else {
                if (params.probabilistic_model) {
                    // use simple probabilistic opponent model
                    HashMap<Types.ACTIONS, Double> agentActionsProb = opponentActionProbs.get(i);

                    Set<Double> values = new HashSet<>(agentActionsProb.values());
                    // if all the probabilities are equal at the current iteration, choose randomly
                    if (values.size() == 1) {
                        int actionIdx = m_rnd.nextInt(gs.nActions());
                        actionsAll[i] = Types.ACTIONS.all().get(actionIdx);
                    } else {
                        // otherwise, choose the most frequent action
                        Map.Entry<Types.ACTIONS, Double> maxEntry = null;
                        for (Map.Entry<Types.ACTIONS, Double> entry : agentActionsProb.entrySet()) {
                            if (maxEntry == null || entry.getValue().compareTo(maxEntry.getValue()) > 0) {
                                maxEntry = entry;
                            }
                        }
                        Types.ACTIONS mostProbableAction = maxEntry.getKey();
                        actionsAll[i] = mostProbableAction;
                    }
                } else{
                    // random action for all of the enemy agents
                    int actionIdx = m_rnd.nextInt(gs.nActions());
                    actionsAll[i] = Types.ACTIONS.all().get(actionIdx);
                }
            }
        }
        gs.next(actionsAll);
    }

    private SingleTreeNode uct(GameState state) {
        SingleTreeNode selected = null;
        double bestValue = -Double.MAX_VALUE;

        for (SingleTreeNode child : this.children) {
            double hvVal = child.totValue;
            double childValue = hvVal / (child.nVisits + params.epsilon);

            childValue = Utils.normalise(childValue, bounds[0], bounds[1]);

            double uctValue = childValue +
                    params.K * Math.sqrt(Math.log(this.nVisits + 1) / (child.nVisits + params.epsilon));

            uctValue = Utils.noise(uctValue, params.epsilon, this.m_rnd.nextDouble());     //break ties randomly

            // small sampleRandom numbers: break ties in unexpanded nodes
            if (uctValue > bestValue) {
                selected = child;
                bestValue = uctValue;
            }
        }
        if (selected == null) {
            throw new RuntimeException("Warning! returning null: " + bestValue + " : " + this.children.length + " " +
                    +bounds[0] + " " + bounds[1]);
        }

        //Roll the state:
        roll(state, actions[selected.childIdx]);

        return selected;
    }

    private double rollOut(GameState state) {
        int thisDepth = this.m_depth;

        while (!finishRollout(state, thisDepth)) {
            int action = safeRandomAction(state);
            roll(state, actions[action]);
            thisDepth++;
        }

        return rootStateHeuristic.evaluateState(state);
    }

    private double pessimisticRollOut(GameState state) {
        int thisDepth = this.m_depth;

        while (thisDepth <= params.pessimistic_simulation_depth && !state.isTerminal()) {
            roll(state, Types.ACTIONS.ACTION_STOP);
            roll(state, Types.ACTIONS.ACTION_STOP);

            int action = safeRandomAction(state);
            roll(state, actions[action]);
            thisDepth++;
        }

        return rootStateHeuristic.evaluateState(state);
    }

    private int safeRandomAction(GameState state) {
        Types.TILETYPE[][] board = state.getBoard();
        ArrayList<Types.ACTIONS> actionsToTry = Types.ACTIONS.all();
        int width = board.length;
        int height = board[0].length;

        while (actionsToTry.size() > 0) {
            int nAction = m_rnd.nextInt(actionsToTry.size());
            Types.ACTIONS act = actionsToTry.get(nAction);
            Vector2d dir = act.getDirection().toVec();

            Vector2d pos = state.getPosition();
            // agent position after taking the action
            int x = pos.x + dir.x;
            int y = pos.y + dir.y;

            if (x >= 0 && x < width && y >= 0 && y < height)
                if (board[y][x] != Types.TILETYPE.FLAMES)
                    return nAction;

            actionsToTry.remove(nAction);
        }

        // None of actions is safe -> return a random one
        return m_rnd.nextInt(num_actions);
    }


    private boolean finishRollout(GameState rollerState, int depth) {
        return depth >= params.rollout_depth || rollerState.isTerminal();
    }

    private void backUp(SingleTreeNode node, double result) {
        SingleTreeNode n = node;

        while (n != null) {
            n.nVisits++;
            n.totValue += result;
            if (result < n.bounds[0]) {
                n.bounds[0] = result;
            }
            if (result > n.bounds[1]) {
                n.bounds[1] = result;
            }
            n = n.parent;
        }
    }

    int mostVisitedAction() {
        int selected = -1;
        double bestValue = -Double.MAX_VALUE;
        boolean allEqual = true;
        double first = -1;

        for (int i = 0; i < children.length; i++) {
            if (children[i] != null) {
                if (first == -1)
                    first = children[i].nVisits;
                else if (first != children[i].nVisits) {
                    allEqual = false;
                }

                double childValue = children[i].nVisits;
                childValue = Utils.noise(childValue, params.epsilon, this.m_rnd.nextDouble());     //break ties randomly
                if (childValue > bestValue) {
                    bestValue = childValue;
                    selected = i;
                }
            }
        }

        if (selected == -1) {
            selected = 0;
        } else if (allEqual) {
            //If all are equal, we opt to choose for the one with the best Q.
            selected = bestAction();
        }

        return selected;
    }

    private int bestAction() {
        int selected = -1;
        double bestValue = -Double.MAX_VALUE;

        for (int i = 0; i < children.length; i++) {
            if (children[i] != null) {
                double childValue = children[i].totValue / (children[i].nVisits + params.epsilon);
                childValue = Utils.noise(childValue, params.epsilon, this.m_rnd.nextDouble());     //break ties randomly

                if (childValue > bestValue) {
                    bestValue = childValue;
                    selected = i;
                }
            }
        }
        if (selected == -1) {
            System.out.println("Unexpected selection!");
            selected = 0;
        }

        return selected;
    }

    private boolean notFullyExpanded() {
        for (SingleTreeNode tn : children) {
            if (tn == null) {
                return true;
            }
        }
        return false;
    }
}
