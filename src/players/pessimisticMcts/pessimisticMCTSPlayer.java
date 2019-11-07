package players.pessimisticMcts;

import core.GameState;
import players.optimisers.ParameterizedPlayer;
import players.Player;
import utils.ElapsedCpuTimer;
import utils.Types;
import utils.Utils;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;

public class pessimisticMCTSPlayer extends ParameterizedPlayer {

    /**
     * Random generator.
     */
    private Random m_rnd;

    /**
     * All actions available.
     */
    public Types.ACTIONS[] actions;

    // ---------------------
    private List<HashMap<Types.ACTIONS, Integer>> opponentActionCounts = new ArrayList<>();
    private List<HashMap<Types.ACTIONS, Double>> opponentActionProbs = new ArrayList<>();
    // ---------------------

    /**
     * Params for this MCTS
     */
    public pMCTSParams params;

    /**
     * Previous game state for opponent action observations
     */
    private GameState prevGS = null;

    public pessimisticMCTSPlayer(long seed, int id) {
        this(seed, id, new pMCTSParams());
    }

    public pessimisticMCTSPlayer(long seed, int id, pMCTSParams params) {
        super(seed, id, params);
        reset(seed, id);

        double defaultProb = 1 / (double)6;

        for (int i = 0; i < 4; i++) {
            HashMap<Types.ACTIONS, Integer> countMap = new HashMap<>();
            countMap.put(Types.ACTIONS.ACTION_STOP, 0);
            countMap.put(Types.ACTIONS.ACTION_BOMB, 0);
            countMap.put(Types.ACTIONS.ACTION_UP, 0);
            countMap.put(Types.ACTIONS.ACTION_DOWN, 0);
            countMap.put(Types.ACTIONS.ACTION_LEFT, 0);
            countMap.put(Types.ACTIONS.ACTION_RIGHT, 0);
            opponentActionCounts.add(countMap);

            HashMap<Types.ACTIONS, Double> probMap = new HashMap<>();
            probMap.put(Types.ACTIONS.ACTION_STOP, defaultProb);
            probMap.put(Types.ACTIONS.ACTION_BOMB, defaultProb);
            probMap.put(Types.ACTIONS.ACTION_UP, defaultProb);
            probMap.put(Types.ACTIONS.ACTION_DOWN, defaultProb);
            probMap.put(Types.ACTIONS.ACTION_LEFT, defaultProb);
            probMap.put(Types.ACTIONS.ACTION_RIGHT, defaultProb);
            opponentActionProbs.add(probMap);
        }

        ArrayList<Types.ACTIONS> actionsList = Types.ACTIONS.all();
        actions = new Types.ACTIONS[actionsList.size()];
        int i = 0;
        for (Types.ACTIONS act : actionsList) {
            actions[i++] = act;
        }
    }

    @Override
    public void reset(long seed, int playerID) {
        super.reset(seed, playerID);
        m_rnd = new Random(seed);

        this.params = (pMCTSParams) getParameters();
        if (this.params == null) {
            this.params = new pMCTSParams();
            super.setParameters(this.params);
        }
    }

//    private int[][] scanBoard(Types.TILETYPE[][] board) {
    private Object[] scanBoard(Types.TILETYPE[][] board) {
        int boardSizeX = board.length;
        int boardSizeY = board[0].length;

        // 4 player positions, (x, y) coordinates
        int[][] positions = new int[4][2];

        ArrayList<int[]> bombs = new ArrayList<>();

        for (int x = 0; x < boardSizeX; x++) {
            for (int y = 0; y < boardSizeY; y++) {
                if (board[y][x] == Types.TILETYPE.AGENT0) {
                    positions[0][0] = x;
                    positions[0][1] = y;
                } else if (board[y][x] == Types.TILETYPE.AGENT1) {
                    positions[1][0] = x;
                    positions[1][1] = y;
                } else if (board[y][x] == Types.TILETYPE.AGENT2) {
                    positions[2][0] = x;
                    positions[2][1] = y;
                } else if (board[y][x] == Types.TILETYPE.AGENT3) {
                    positions[3][0] = x;
                    positions[3][1] = y;
                } else if (board[y][x] == Types.TILETYPE.BOMB) {
                    int[] coords = new int[2];
                    coords[0] = x;
                    coords[1] = y;
                    bombs.add(coords);
                }
            }
        }
//        for (int i = 0; i < bombs.size(); i++) {
//            positions[4 + i] = bombs.get(i);
//        }
//        return positions;
        return new Object[]{positions, bombs};
    }

    private boolean checkBombAtCoords(int[][] positions, int x, int y) {
        // go through all bombs
        for (int i = 4; i < 8; i++) {
            if (positions[i][0] == x && positions[i][1] == y) {
                return true;
            }
        }
        return false;
    }

    private Types.ACTIONS[] observeAgentsActions(GameState presGS, GameState gs) {
        Types.TILETYPE[][] prevBoard = presGS.getBoard();
        Types.TILETYPE[][] curBoard = gs.getBoard();

        Types.ACTIONS[] actionsTaken = new Types.ACTIONS[4];

//        int playerId = gs.getPlayerId() - Types.TILETYPE.AGENT0.getKey();
//        System.out.println(playerId);

//        int[][] curPositions = scanBoard(curBoard);
//        int[][] prevPositions = scanBoard(prevBoard);

        Object[] curScan = scanBoard(curBoard);
        int[][] curPositions = (int[][])curScan[0];
        ArrayList<int[]> curBombs = (ArrayList<int[]>)curScan[1];
//        int[][] prevPositions = scanBoard(prevBoard);

        Object[] prevScan = scanBoard(prevBoard);
        int[][] prevPositions = (int[][])prevScan[0];
        ArrayList<int[]> prevBombs = (ArrayList<int[]>)prevScan[1];


        for (int iAgent = 0; iAgent < 4; iAgent++) {
            if (prevPositions[iAgent][0] > curPositions[iAgent][0]) {
                actionsTaken[iAgent] = Types.ACTIONS.ACTION_LEFT;
            } else if (prevPositions[iAgent][0] < curPositions[iAgent][0]) {
                actionsTaken[iAgent] = Types.ACTIONS.ACTION_RIGHT;
            } else if (prevPositions[iAgent][1] < curPositions[iAgent][1]) {
                actionsTaken[iAgent] = Types.ACTIONS.ACTION_DOWN;
            } else if (prevPositions[iAgent][1] > curPositions[iAgent][1]) {
                actionsTaken[iAgent] = Types.ACTIONS.ACTION_UP;
            } else if (curPositions[iAgent][0] == prevPositions[iAgent][0] &&
                    curPositions[iAgent][1] == prevPositions[iAgent][1]) {
                // Its either STOP or BOMB
                int cur_x = curPositions[iAgent][0];
                int cur_y = curPositions[iAgent][1];
                boolean isBomb = checkBombAtCoords(curPositions, cur_x, cur_y);
                boolean isPrevBomb = checkBombAtCoords(prevPositions, cur_x, cur_y);

                if (isBomb && !isPrevBomb)
                    actionsTaken[iAgent] = Types.ACTIONS.ACTION_BOMB;
                else
                    actionsTaken[iAgent] = Types.ACTIONS.ACTION_STOP;
            }
        }
        return actionsTaken;
    }

    private void updateOpponentActionProbs(Types.ACTIONS[] actions) {
        for (int i = 0; i < this.opponentActionCounts.size(); i++) {
            Types.ACTIONS performedAction = actions[i];
            HashMap<Types.ACTIONS, Integer> curOpponentCountMap = opponentActionCounts.get(i);

            if (curOpponentCountMap.containsKey(performedAction)) {
                curOpponentCountMap.put(performedAction, curOpponentCountMap.get(performedAction) + 1);
            } else {
                curOpponentCountMap.put(performedAction, 1);
            }

            // calculate total count of actions for the current agent
            int totalCount = 0;
            for (int actCount : curOpponentCountMap.values()) {
                totalCount += actCount;
            }

            HashMap<Types.ACTIONS, Double> curOpponentProbMap = opponentActionProbs.get(i);

            for (Types.ACTIONS action : Types.ACTIONS.all()) {
                int actCount = curOpponentCountMap.get(action);
                double actProb = actCount / (double)totalCount;

                double curProb = curOpponentProbMap.get(action);
                double newProb = (curProb + actProb) / 2;

                curOpponentProbMap.put(action, newProb);
            }
        }
    }

    @Override
    public Types.ACTIONS act(GameState gs) {

        // TODO update gs
        if (gs.getGameMode().equals(Types.GAME_MODE.TEAM_RADIO)){
            int[] msg = gs.getMessage();
        }

        ElapsedCpuTimer ect = new ElapsedCpuTimer();
        ect.setMaxTimeMillis(params.num_time);

        // Number of actions available
        int num_actions = actions.length;

        Types.ACTIONS[] actionsTaken = null;

        if (this.prevGS != null) {
            // calculate the difference between states
            actionsTaken = observeAgentsActions(this.prevGS, gs);
            updateOpponentActionProbs(actionsTaken);
        }

        // Root of the tree
        SingleTreeNode m_root = new SingleTreeNode(params, m_rnd, num_actions,
                actions, opponentActionProbs);
        m_root.setRootGameState(gs);

        //Determine the action using MCTS...
        m_root.mctsSearch(ect);

        //Determine the best action to take and return it.
        int action = m_root.mostVisitedAction();

        // save the current game state for the next iteration
        this.prevGS = gs;

        // TODO update message memory

        //... and return it.
        return actions[action];
    }

    @Override
    public int[] getMessage() {
        // default message
        int[] message = new int[Types.MESSAGE_LENGTH];
        message[0] = 1;
        return message;
    }

    @Override
    public Player copy() {
        return new pessimisticMCTSPlayer(seed, playerID, params);
    }
}