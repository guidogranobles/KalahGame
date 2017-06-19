package co.com.kalah.game.component;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import co.com.kalah.game.dto.GamePlayStatus;
import co.com.kalah.game.dto.GamePlayUpdate;
import co.com.kalah.game.model.GameBoard;
import co.com.kalah.game.model.User;
import co.com.kalah.game.service.GameBoardService;
import co.com.kalah.game.service.UserService;

/**
 * ****************************************************************.
 * 
 * @autor: Guido Granobles
 * @fecha: 15/10/2016 @descripci�n: This class manage all the game operations.
 * @copyright: Copyright � 2016 GG.
 *             ****************************************************************
 */

@Controller
public class GamePlay {

	@Autowired
	private SimpMessagingTemplate template;

	@Autowired
	UserService userService;

	@Autowired
	GameBoardService gameBoardService;

	/**
	 * ****************************************************************.
	 * 
	 * @metodo: startGame
	 * @descripcion: it starts a new game.
	 * @param players
	 * @return: The GamePlayStatus object contains all the info about the
	 *          current game started.
	 * @autor: Guido Granobles
	 *****************************************************************
	 */
	@MessageMapping("/startGame")
	@SendTo("/queue/playerUpdates")
	public void startGame(String player2Username, Principal principal) {

		User player1 = null;
		User player2 = null;

		GamePlayStatus gameStatus = new GamePlayStatus();

		player1 = userService.findUserByUsername(principal.getName());
		player2 = userService.findUserByUsername(player2Username);

		// This is used in order to indentify one session game of two players
		// from others.
		String hashIdnewGameBoard = player1.getId() + "" + player2.getId() + Long.toString(System.currentTimeMillis());

		GameBoard gameBoardPlayer1 = new GameBoard();
		gameBoardPlayer1.setIdBoard(hashIdnewGameBoard);
		gameBoardPlayer1.setIduser(player1.getId());
		gameBoardPlayer1.initPits();
		gameBoardPlayer1.setStatus("Initiated");
		gameBoardService.save(gameBoardPlayer1);

		GameBoard gameBoardPlayer2 = new GameBoard();
		gameBoardPlayer2.setIdBoard(hashIdnewGameBoard);
		gameBoardPlayer2.setIduser(player2.getId());
		gameBoardPlayer2.initPits();
		// Status Initiated indicates that the game just started.
		gameBoardPlayer2.setStatus("Initiated");
		gameBoardService.save(gameBoardPlayer2);

		gameStatus.setIdPlayer1(player1.getId());
		gameStatus.setIdPlayer2(player2.getId());
		gameStatus.setIdBoard(hashIdnewGameBoard);

		// Status next indicates that the game is ready for the next player to
		// play
		gameStatus.setStatus("next");

		Map<String, Object> msg = new HashMap<String, Object>();
		msg.put("TYPE", "GAME_STARTED");
		msg.put("PAYLOAD", gameStatus);
		gameStatus.setCurrentPlayer(principal.getName());
		template.convertAndSendToUser(principal.getName(), "/queue/playerUpdates", msg);		
		gameStatus.setCurrentPlayer(player2Username);
		template.convertAndSendToUser(player2Username, "/queue/playerUpdates", msg);

	}

	/**
	 * ****************************************************************.
	 * 
	 * @metodo: updatePlayerBoard
	 * @descripcion: update boards using the given info. The object updateInfo
	 *               should contain the idBoard, players and the pit where we
	 *               should start. We start from the given pit adding stones to
	 *               one by one to the pits at the right side.
	 * @param hashIdBoard
	 * @return: The GamePlayStatus object contains all the current info about a
	 *          specific session game.
	 * @autor: Guido Granobles
	 *****************************************************************
	 */
	@MessageMapping("/updatePlayerBoard")
	@SendTo("/queue/playerUpdates")
	public void updatePlayerBoard(GamePlayUpdate updateInfo, Principal principal) {

		GameBoard gameBoardPlayer1 = null;
		GameBoard gameBoardPlayer2 = null;
		boolean pitWasEmpty = false;
		String status = "next";
		
		User curPlayerSession = userService.findUserByUsername(principal.getName());
		
		if(curPlayerSession.getId()==updateInfo.getIdCurrentPlayer()){
			 gameBoardPlayer1 = loadPlayerBoard(updateInfo.getIdBoard(), updateInfo.getIdCurrentPlayer());
			 gameBoardPlayer2 = loadPlayerBoard(updateInfo.getIdBoard(), updateInfo.getIdSecondPlayer());
		}else{
			 gameBoardPlayer1 = loadPlayerBoard(updateInfo.getIdBoard(), updateInfo.getIdSecondPlayer());
			 gameBoardPlayer2 = loadPlayerBoard(updateInfo.getIdBoard(), updateInfo.getIdCurrentPlayer());
		}

		// set the given pit to 0 and get the amount of stones that were there.
		int stonesToDist = emptyPit(gameBoardPlayer1, updateInfo.getPitToEmpty());

		// initial position for the first stone to put. One pit to the right.
		int pitPosition = updateInfo.getPitToEmpty() + 1;

		while (stonesToDist > 0) {

			if (pitPosition <= 6) {
				pitWasEmpty = addStoneToPit(gameBoardPlayer1, pitPosition);
			} else if (pitPosition == 7) {
				gameBoardPlayer1.setKalah(gameBoardPlayer1.getKalah() + 1);
				// if the last stone was put in the Kalah, then repeat turn;
				if (stonesToDist == 1) {
					status = "repeat";
				}

			} else {
				// If we are here is because position is greater than 7, so it's
				// time to switch to the other side of the board
				pitWasEmpty = addStoneToPit(gameBoardPlayer2, pitPosition - 7);
			} 

			stonesToDist--;
			
			// if we put the last stone in an empty pit and that pit belongs to
			// the current player,
			// then take everything from the pit in front and put it in the
			// current player Kalah plus the last stone.
			if (pitWasEmpty && pitPosition < 7 && stonesToDist == 0) {
				int stonesFrontPit = emptyPit(gameBoardPlayer2, 7 - pitPosition);
				gameBoardPlayer1.setKalah(gameBoardPlayer1.getKalah() + stonesFrontPit + 1);
				emptyPit(gameBoardPlayer1, pitPosition);
			}
			
			pitPosition++;
			
			if (pitPosition == 14){
				pitPosition = 1;
			}

		}

		gameBoardService.save(gameBoardPlayer1);
		gameBoardService.save(gameBoardPlayer2);

		GamePlayStatus gamePlayStatus = loadPlayersBoard(gameBoardPlayer1.getIdBoard());
		Integer idWinner = checkWinner(gameBoardPlayer1, gameBoardPlayer2);
		User playerWinner = userService.findUserById(idWinner);
		if (idWinner != null) {
			status = "winner";
			gamePlayStatus.setIdWinner(idWinner);
			gamePlayStatus.setNameWinner(playerWinner.getUsername());
		}

		gamePlayStatus.setStatus(status);
		gamePlayStatus.setCurrentPlayer(principal.getName());

		Map<String, Object> msg = new HashMap<String, Object>();
		msg.put("TYPE", "GAME_STATE_CHANGED");
		msg.put("PAYLOAD", gamePlayStatus);
		template.convertAndSendToUser(principal.getName(), "/queue/playerUpdates", msg);

	}
	
	
	@MessageMapping("/notifySecondPlayer")
	@SendTo("/queue/playerUpdates")
	public void notifySecondPlayer(GamePlayStatus gamePlayStatus) {
		
		User player2 = userService.findUserByUsername(gamePlayStatus.getCurrentPlayer());
		
		Map<String, Object> msg = new HashMap<String, Object>();
		msg.put("TYPE", "GAME_NEXT_MOVE");
		msg.put("PAYLOAD", gamePlayStatus);
		template.convertAndSendToUser(player2.getUsername(), "/queue/playerUpdates", msg);
		
	}

	private boolean addStoneToPit(GameBoard playerBoard, Integer pitNumber) {

		boolean isPitEmpty = false;

		switch (pitNumber) {

		case 1:
			isPitEmpty = (playerBoard.getPit1() == 0);
			playerBoard.setPit1(playerBoard.getPit1() + 1);
			break;
		case 2:
			isPitEmpty = (playerBoard.getPit2() == 0);
			playerBoard.setPit2(playerBoard.getPit2() + 1);
			break;
		case 3:
			isPitEmpty = (playerBoard.getPit3() == 0);
			playerBoard.setPit3(playerBoard.getPit3() + 1);
			break;
		case 4:
			isPitEmpty = (playerBoard.getPit4() == 0);
			playerBoard.setPit4(playerBoard.getPit4() + 1);
			break;
		case 5:
			isPitEmpty = (playerBoard.getPit5() == 0);
			playerBoard.setPit5(playerBoard.getPit5() + 1);
			break;
		case 6:
			isPitEmpty = (playerBoard.getPit6() == 0);
			playerBoard.setPit6(playerBoard.getPit6() + 1);
			break;
		}

		return isPitEmpty;
	}

	private int emptyPit(GameBoard playerBoard, Integer pitNumber) {

		Integer currentPitStones = 0;

		switch (pitNumber) {

		case 1:
			currentPitStones = playerBoard.getPit1();
			playerBoard.setPit1(0);
			break;
		case 2:
			currentPitStones = playerBoard.getPit2();
			playerBoard.setPit2(0);
			break;
		case 3:
			currentPitStones = playerBoard.getPit3();
			playerBoard.setPit3(0);
			break;
		case 4:
			currentPitStones = playerBoard.getPit4();
			playerBoard.setPit4(0);
			break;
		case 5:
			currentPitStones = playerBoard.getPit5();
			playerBoard.setPit5(0);
			break;
		case 6:
			currentPitStones = playerBoard.getPit6();
			playerBoard.setPit6(0);
			break;
		}

		return currentPitStones;

	}

	private GameBoard loadPlayerBoard(String hashIdBoard, Integer idPlayer) {

		return gameBoardService.findByIdboardAndIduser(hashIdBoard, idPlayer);

	}

	private Integer checkWinner(GameBoard currentPlayerBoard, GameBoard secondPlayerBoard) {

		Integer idPlayerWinner = null;

		if (checkTotalStonesInPits(currentPlayerBoard) == 0) {
			secondPlayerBoard.setKalah(secondPlayerBoard.getKalah() + checkTotalStonesInPits(secondPlayerBoard));
			idPlayerWinner = currentPlayerBoard.getKalah() > secondPlayerBoard.getKalah()
					? currentPlayerBoard.getIduser() : secondPlayerBoard.getIduser();
		} else if (checkTotalStonesInPits(secondPlayerBoard) == 0) {
			currentPlayerBoard.setKalah(currentPlayerBoard.getKalah() + checkTotalStonesInPits(currentPlayerBoard));
			idPlayerWinner = currentPlayerBoard.getKalah() > secondPlayerBoard.getKalah()
					? currentPlayerBoard.getIduser() : secondPlayerBoard.getIduser();
		}

		return idPlayerWinner;
	}

	private int checkTotalStonesInPits(GameBoard playerBoard) {

		return (playerBoard.getPit1() + playerBoard.getPit2() + playerBoard.getPit3() + playerBoard.getPit4()
				+ playerBoard.getPit5() + playerBoard.getPit6());

	}

	/**
	 * ****************************************************************.
	 * 
	 * @metodo: loadPlayersBoard
	 * @descripcion: it loads the board using an unique identifier for both
	 *               players.
	 * @param hashIdBoard
	 * @return: The GamePlayStatus object contains all the current info about a
	 *          specific session game.
	 * @autor: Guido Granobles
	 *****************************************************************
	 */
	private GamePlayStatus loadPlayersBoard(String hashIdBoard) {

		GamePlayStatus gameStatus = new GamePlayStatus();

		// load the two game boards for the given idBoard. There's just one
		// board (6 pits + kalah) for each player

		List<GameBoard> lResults = gameBoardService.findByIdboard(hashIdBoard);

		// We should have always 2 boards one for each player.
		if (lResults.size() != 2) {
			return null;
		}

		gameStatus.setIdPlayer1(lResults.get(0).getIduser());
		gameStatus.setIdPlayer2(lResults.get(1).getIduser());
		gameStatus.setIdBoard(hashIdBoard);

		gameStatus.getBoardPlayer1().setKalah(lResults.get(0).getKalah());
		gameStatus.getBoardPlayer1().setPit1(lResults.get(0).getPit1());
		gameStatus.getBoardPlayer1().setPit2(lResults.get(0).getPit2());
		gameStatus.getBoardPlayer1().setPit3(lResults.get(0).getPit3());
		gameStatus.getBoardPlayer1().setPit4(lResults.get(0).getPit4());
		gameStatus.getBoardPlayer1().setPit5(lResults.get(0).getPit5());
		gameStatus.getBoardPlayer1().setPit6(lResults.get(0).getPit6());

		gameStatus.getBoardPlayer2().setKalah(lResults.get(1).getKalah());
		gameStatus.getBoardPlayer2().setPit1(lResults.get(1).getPit1());
		gameStatus.getBoardPlayer2().setPit2(lResults.get(1).getPit2());
		gameStatus.getBoardPlayer2().setPit3(lResults.get(1).getPit3());
		gameStatus.getBoardPlayer2().setPit4(lResults.get(1).getPit4());
		gameStatus.getBoardPlayer2().setPit5(lResults.get(1).getPit5());
		gameStatus.getBoardPlayer2().setPit6(lResults.get(1).getPit6());

		return gameStatus;

	}

}
