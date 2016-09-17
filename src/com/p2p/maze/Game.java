package com.p2p.maze;

import java.net.InetAddress;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Scanner;

/**
 * Game
 * <p>
 * Player in the maze game
 * First player join the game will become primary server
 * Second player join the game will become secondary server
 * In the event of primary or secondary server crash, the system should promote a new pair of primary/secondary servers
 */
public class Game implements GameInterface {

  public enum Command {
    GAME_STATE(0),
    MOVE_WEST(1),
    MOVE_SOUTH(2),
    MOVE_EAST(3),
    MOVE_NORTH(4),
    EXIT(9);

    private final int id;
    Command(int id) { this.id = id; }
    public int getValue() { return id; }
  }

  private Player player;
  private Registry serverRegistry;
  private GameState gameState;

  public Game(String playerId, String localServerIp, int portNumber) {
    this.player = new Player(playerId, localServerIp, portNumber);
  }

  /**
   * Contact tracker when player join the game, receive tracker state: n, k, primary, backup
   *
   * @param registry rmiRegistry
   * @throws RemoteException
   * @throws NotBoundException
   */
  private void contactTracker(Registry registry) throws RemoteException, NotBoundException {
    TrackerInterface stub = (TrackerInterface) registry.lookup("Tracker");
    TrackerState trackerState = stub.register(player);
    System.out.println("Tracker response: " + trackerState.toString());
    gameState = new GameState(trackerState);
//    gameState.setPrimary(trackerState.getPrimary());
//    gameState.setBackup(trackerState.getBackup());
    System.out.println("TrackerState response: " + gameState.toString());

  }

  private void init() throws RemoteException, NotBoundException {

    Player primaryPlayer = gameState.getPrimary();

    primaryPlayer = primaryPlayer != null ? primaryPlayer : gameState.getBackup();

    if (primaryPlayer != null && !primaryPlayer.getPlayerId().equals(player.getPlayerId())){
      serverRegistry = LocateRegistry.getRegistry(primaryPlayer.getIp(), primaryPlayer.getPortNumber());
      GameInterface stub = (GameInterface) serverRegistry.lookup(primaryPlayer.getPlayerId());
      updateGameState(stub.initPlayer(player));
    }  else {
      gameState.initGameState();
      gameState.addNewPlayer(player);
      updatePlayer();
    }

    System.out.println("game state after init: " + gameState.toString());
    System.out.println("player after init: " + player.toString());
  }

  private void updateGameState(GameState gameState){
    this.gameState = gameState;
    updatePlayer();

    System.out.println("game state after move: " + gameState.toString());
    System.out.println("player after move: " + player.toString());
  }

  private void updatePlayer(){
    String playerId = player.getPlayerId();
    Position position = gameState.getPlayerPosition().get(playerId);
    if (position != null){
      player.setPosition(position);
    }

    Integer score = gameState.getScoreList().get(playerId);
    if (score != null){
      player.setScore(score);
    }
  }

  private void run() {
    System.out.println("Player is playing: " + player.getPlayerId());
    Scanner scanner = new Scanner(System.in);
    while (true) {

      String input = scanner.nextLine();
      if (input == null || input.length() == 0){
        continue;
      }

      if ("q".equals(input)) {
        System.out.println("Exit!");
        System.exit(0);
      }

      System.out.println("input : " + input);
      System.out.println("-----------\n");
      Player primaryPlayer = gameState.getPrimary();
      try {
        serverRegistry = LocateRegistry.getRegistry(primaryPlayer.getIp(), primaryPlayer.getPortNumber());
        GameInterface stub = (GameInterface) serverRegistry.lookup(primaryPlayer.getPlayerId());
        switch (input.charAt(0)){

          case '0':
            updateGameState(stub.getGameState());
            break;
          case '1':
            updateGameState(stub.move(player, Command.MOVE_WEST));
            break;
          case '2':
            updateGameState(stub.move(player, Command.MOVE_SOUTH));
            break;
          case '3':
            updateGameState(stub.move(player, Command.MOVE_EAST));
            break;
          case '4':
            updateGameState(stub.move(player, Command.MOVE_NORTH));
            break;
          case '5':
            stub.exit(player);
            break;

        }
      } catch (RemoteException e) {
        e.printStackTrace();
      } catch (NotBoundException e) {
        e.printStackTrace();
      }
    }
  }

  public static void main(String[] args) {
    if (args.length < 3) {
      System.err.println("Invalid input to start the game");
      return;
    }

    System.err.println("s1");
    String trackerIpAddress = args[0];
    int portNumber = Integer.parseInt(args[1]);
    String playerId = args[2];
    if (playerId == null || playerId.length() != 2) {
      System.err.println("Invalid player id");
      return;
    }

    try {
      System.err.println("s2");

      InetAddress IP=InetAddress.getLocalHost();
      System.out.println("IP of my system is := "+IP.getHostAddress());
      String localServerIp = IP.getHostAddress();

      Game game = new Game(playerId, localServerIp, portNumber);

      // get Tracker registry
      Registry registry = LocateRegistry.getRegistry(trackerIpAddress, portNumber);

      game.contactTracker(registry);

      game.init();

      GameInterface iGame = (GameInterface) UnicastRemoteObject.exportObject(game, 0);

      // Bind the remote object's stub in the registry

      Registry playerRegistry = LocateRegistry.getRegistry(localServerIp, portNumber);
      playerRegistry.rebind(playerId, iGame);

      System.out.println("Player ready: " + playerId);

      // infinite loop that waits for user input and make a move
      game.run();

    } catch (Exception e) {
      System.err.println("Client exception: " + e.toString());
      e.printStackTrace();
    }
  }

  @Override
  public void setPrimary(String primary) throws RemoteException {

  }

  @Override
  public void setBackup(String backup) throws RemoteException {

  }

  @Override
  public GameState initPlayer(Player player) throws RemoteException {
    gameState.addNewPlayer(player);
    return gameState;
  }

  @Override
  public GameState getGameState() throws RemoteException {
    return gameState;
  }

  @Override
  public GameState move(Player player, Command move) throws RemoteException {
    switch (move) {
      case MOVE_WEST:
        gameState.move(player, 0, -1);
        break;

      case MOVE_SOUTH:
        gameState.move(player, 1, 0);
        break;

      case MOVE_EAST:
        gameState.move(player, 0, 1);
        break;

      case MOVE_NORTH:
        gameState.move(player, -1, 0);
        break;

      default:
        System.err.println("Unrecognized move command");
    }
    return gameState;
  }

  @Override
  public void exit(Player player) throws RemoteException {

  }

  @Override
  public boolean ping() throws RemoteException {
    return false;
  }

  @Override
  public boolean syncGameState() throws RemoteException {
    return false;
  }

  @Override
  public void promoteToBackupServer(String playerId, GameState gameState) throws RemoteException {

  }
}
