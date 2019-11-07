import core.Game;
import players.*;
import utils.Types;
import players.mcts.MCTSParams;
import players.mcts.MCTSPlayer;
import players.pessimisticMcts.pMCTSParams;
import players.pessimisticMcts.pessimisticMCTSPlayer;
import players.rhea.utils.Constants;
import players.rhea.utils.RHEAParams;
import players.rhea.RHEAPlayer;


import java.util.ArrayList;

public class Test {

    public static void main(String[] args) {

        // Game parameters
        long seed = System.currentTimeMillis();
        int boardSize = Types.BOARD_SIZE;
        Types.GAME_MODE gameMode = Types.GAME_MODE.FFA;
        boolean useSeparateThreads = false;

        Game game = new Game(seed, boardSize, Types.GAME_MODE.FFA, "");

        // Key controllers for human player s (up to 2 so far).
        KeyController ki1 = new KeyController(true);
        KeyController ki2 = new KeyController(false);

        // Create players
        ArrayList<Player> players = new ArrayList<>();
        int playerID = Types.TILETYPE.AGENT0.getKey();

        MCTSParams mctsParams = new MCTSParams();
        mctsParams.stop_type = mctsParams.STOP_ITERATIONS;
        mctsParams.heuristic_method = mctsParams.CUSTOM_HEURISTIC;

        RHEAParams rheaParams = new RHEAParams();
        rheaParams.heurisic_type = Constants.CUSTOM_HEURISTIC;

//        pMCTSParams pMCTSParams = new pMCTSParams();
        pMCTSParams pMCTSParams = new pMCTSParams();
//        pMCTSParams.stop_type = pMCTSParams.STOP_TIME;
//        pMCTSParams.num_time = 90;
        pMCTSParams.stop_type = mctsParams.STOP_ITERATIONS;
        pMCTSParams.num_iterations = 100;
        pMCTSParams.search_depth = 2;
        pMCTSParams.pessimistic_simulation_depth = 4;
        pMCTSParams.rollout_depth = 10;
//        pMCTSParams.heuristic_method = pMCTSParams.CUSTOM_HEURISTIC;
//        pMCTSParams.heuristic_method = pMCTSParams.MODIFIED_ADVANCED_HEURISTIC;
        pMCTSParams.heuristic_method = pMCTSParams.ADVANCED_HEURISTIC;

        players.add(new pessimisticMCTSPlayer(seed, playerID++, pMCTSParams));
//        players.add(new MCTSPlayer(seed, playerID++, mctsParams));

        players.add(new SimplePlayer(seed, playerID++));
//        players.add(new RHEAPlayer(seed, playerID++, rheaParams));
        players.add(new SimplePlayer(seed, playerID++));
//        players.add(new SimplePlayer(seed, playerID++));
        players.add(new HumanPlayer(ki1, playerID++));
//        players.add(new MCTSPlayer(seed, playerID++, new MCTSParams()));
//        players.add(new RHEAPlayer(seed, playerID++, rheaParams));

        // Make sure we have exactly NUM_PLAYERS players
        assert players.size() == Types.NUM_PLAYERS : "There should be " + Types.NUM_PLAYERS +
                " added to the game, but there are " + players.size();


        //Assign players and run the game.
        game.setPlayers(players);

        //Run a single game with the players
        Run.runGame(game, ki1, ki2, useSeparateThreads);

        /* Uncomment to run the replay of the previous game: */
//        if (game.isLogged()){
//            Game replay = Game.getLastReplayGame();
//            Run.runGame(replay, ki1, ki2, useSeparateThreads);
//            assert(replay.getGameState().equals(game.getGameState()));
//        }



        /* Run with no visuals, N Times: */
//        int N = 20;
//        Run.runGames(game, new long[]{seed}, N, useSeparateThreads);

    }

}
