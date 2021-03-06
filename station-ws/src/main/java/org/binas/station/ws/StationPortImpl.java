package org.binas.station.ws;

import javax.jws.WebService;

import org.binas.station.domain.Coordinates;
import org.binas.station.domain.Station;
import org.binas.station.domain.exception.BadInitException;
import org.binas.station.domain.exception.InvalidCreditException;
import org.binas.station.domain.exception.NoBinaAvailException;
import org.binas.station.domain.exception.NoSlotAvailException;
import org.binas.station.domain.exception.NoUserFoundException;

/**
 * This class implements the Web Service port type (interface). The annotations
 * below "map" the Java class to the WSDL definitions.
 */
@WebService(endpointInterface = "org.binas.station.ws.StationPortType",
            wsdlLocation = "station.2_0.wsdl",
            name ="StationWebService",
            portName = "StationPort",
            targetNamespace="http://ws.station.binas.org/",
            serviceName = "StationService"
)
public class StationPortImpl implements StationPortType {

	/**
	 * The Endpoint manager controls the Web Service instance during its whole
	 * lifecycle.
	 */
	private StationEndpointManager endpointManager;

	/** Constructor receives a reference to the endpoint manager. */
	public StationPortImpl(StationEndpointManager endpointManager) {
		this.endpointManager = endpointManager;
	}
	
	// Main operations -------------------------------------------------------
	
	/** Retrieve information about station. */
	@Override
	public StationView getInfo() {
		// Access the domain root where the station master data is stored.
		Station station = Station.getInstance();
		// Create a view (copy) to store the station data in the response.
		// Acquire station object lock to perform all gets together.
		synchronized(station) {
			return buildStationView(station);
		}
	}

	/** Return a bike to the station. */
	@Override
	public int returnBina() throws NoSlotAvail_Exception {
		Station station = Station.getInstance();
		int bonus = 0;
		try {
			bonus = station.returnBina();
		} catch(NoSlotAvailException e) {
			throwNoSlotAvail("No slot available at this station!");
		}
		return bonus;
	}

	/** Take a bike from the station. */
	@Override
	public void getBina() throws NoBinaAvail_Exception {
		Station station = Station.getInstance();
		try {
			station.getBina();
		} catch (NoBinaAvailException e) {
			throwNoBinaAvail("No Bina available at this station!");
		}
	}
	
	@Override
	public BalanceView getBalance(String email) {
		Station station = Station.getInstance();
		//System.out.println("Looking for: " + email + "\n");
		return station.getBalance(email);
	}
	
	@Override
	public void setBalance(String email,int credit, int tag) throws InvalidCredit_Exception{
		Station station = Station.getInstance();
		BalanceView view = buildBalanceView(credit,tag);
		try {
			station.setBalance(email,view);
		} catch (InvalidCreditException e) {
			throwInvalidCredit("Credit cannot be negative");
		}
	
	}

	// Test Control operations -----------------------------------------------

	/** Diagnostic operation to check if service is running. */
	@Override
	public String testPing(String inputMessage) {
		// If no input is received, return a default name.
		if (inputMessage == null || inputMessage.trim().length() == 0)
			inputMessage = "friend";

		// If the station does not have a name, return a default.
		String wsName = endpointManager.getWsName();
		if (wsName == null || wsName.trim().length() == 0)
			wsName = "Station";

		// Build a string with a message to return.
		StringBuilder builder = new StringBuilder();
		builder.append("Hello ").append(inputMessage);
		builder.append(" from ").append(wsName);
		return builder.toString();
	}

	/** Return all station variables to default values. */
	@Override
	public void testClear() {
		Station.getInstance().reset();
	}

	/** Set station variables with specific values. */
	@Override
	public void testInit(int x, int y, int capacity, int returnPrize) throws BadInit_Exception {
		try {
			Station.getInstance().init(x, y, capacity, returnPrize);
		} catch (BadInitException e) {
			throwBadInit("Invalid initialization values!");
		}
	}

	// View helpers ----------------------------------------------------------

	/** Helper to convert a domain station to a view. */
	private StationView buildStationView(Station station) {
		StationView view = new StationView();
		view.setId(station.getId());
		view.setCoordinate(buildCoordinatesView(station.getCoordinates()));
		view.setCapacity(station.getMaxCapacity());
		view.setTotalGets(station.getTotalGets());
		view.setTotalReturns(station.getTotalReturns());
		view.setFreeDocks(station.getFreeDocks());
		view.setAvailableBinas(station.getAvailableBinas());
		return view;
	}

	/** Helper to convert a domain coordinates to a view. */
	private CoordinatesView buildCoordinatesView(Coordinates coordinates) {
		CoordinatesView view = new CoordinatesView();
		view.setX(coordinates.getX());
		view.setY(coordinates.getY());
		return view;
	}
	private BalanceView buildBalanceView(int credit,int tag) {
		BalanceView view = new BalanceView();
		view.setCredit(credit);
		view.setTag(tag);
		return view;
	}
	
	// Exception helpers -----------------------------------------------------

	/** Helper to throw a new NoBinaAvail exception. */
	private void throwNoBinaAvail(final String message) throws NoBinaAvail_Exception {
		NoBinaAvail faultInfo = new NoBinaAvail();
		faultInfo.message = message;
		throw new NoBinaAvail_Exception(message, faultInfo);
	}
	
	/** Helper to throw a new NoSlotAvail exception. */
	private void throwNoSlotAvail(final String message) throws NoSlotAvail_Exception {
		NoSlotAvail faultInfo = new NoSlotAvail();
		faultInfo.message = message;
		throw new NoSlotAvail_Exception(message, faultInfo);
	}

	/** Helper to throw a new BadInit exception. */
	private void throwBadInit(final String message) throws BadInit_Exception {
		BadInit faultInfo = new BadInit();
		faultInfo.message = message;
		throw new BadInit_Exception(message, faultInfo);
	}
	
	/** Helper to throw a new InvalidCredit exception. */
	private void throwInvalidCredit(final String message) throws InvalidCredit_Exception {
		InvalidCredit faultInfo = new InvalidCredit();
		faultInfo.message = message;
		throw new InvalidCredit_Exception(message, faultInfo);
	}
	
	/** Helper to throw a new InvalidCredit exception. */
	private void throwNoUserFound(final String message) throws NoUserFound_Exception {
		NoUserFound faultInfo = new NoUserFound();
		faultInfo.message = message;
		throw new NoUserFound_Exception(message, faultInfo);
	}

}
