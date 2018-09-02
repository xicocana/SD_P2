package org.binas.station.domain.exception;


public class NoUserFoundException extends Exception {
	private static final long serialVersionUID = 1L;

	public NoUserFoundException() {
	}

	public  NoUserFoundException(String message) {
		super(message);
	}
}
