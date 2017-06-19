package co.com.kalah.game.dto;

import java.io.Serializable;

public class GamePlayUpdate implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Integer idCurrentPlayer;
	private Integer idSecondPlayer;
	private String idBoard;
	private Integer pitToEmpty;

	
	public Integer getIdCurrentPlayer() {
		return idCurrentPlayer;
	}

	public void setIdCurrentPlayer(Integer idCurrentPlayer) {
		this.idCurrentPlayer = idCurrentPlayer;
	}
	
	

	public Integer getIdSecondPlayer() {
		return idSecondPlayer;
	}

	public void setIdSecondPlayer(Integer idSecondPlayer) {
		this.idSecondPlayer = idSecondPlayer;
	}

	public String getIdBoard() {
		return idBoard;
	}

	public void setIdBoard(String idBoard) {
		this.idBoard = idBoard;
	}

	public Integer getPitToEmpty() {
		return pitToEmpty;
	}

	public void setPitToEmpty(Integer pitToEmpty) {
		this.pitToEmpty = pitToEmpty;
	}

	
}


