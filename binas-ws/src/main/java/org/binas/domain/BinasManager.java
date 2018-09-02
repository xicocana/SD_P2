package org.binas.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import org.binas.domain.exception.BadInitException;
import org.binas.domain.exception.InsufficientCreditsException;
import org.binas.domain.exception.InvalidEmailException;
import org.binas.domain.exception.StationNotFoundException;
import org.binas.domain.exception.UserAlreadyExistsException;
import org.binas.domain.exception.UserAlreadyHasBinaException;
import org.binas.domain.exception.UserHasNoBinaException;
import org.binas.domain.exception.UserNotFoundException;
import org.binas.station.ws.BadInit_Exception;
import org.binas.station.ws.BalanceView;
import org.binas.station.ws.GetBalanceResponse;
import org.binas.station.ws.InvalidCredit_Exception;
import org.binas.station.ws.NoBinaAvail_Exception;
import org.binas.station.ws.NoSlotAvail_Exception;
import org.binas.station.ws.NoUserFound_Exception;
import org.binas.station.ws.SetBalanceResponse;
import org.binas.station.ws.cli.StationClient;
import org.binas.station.ws.cli.StationClientException;
import org.binas.ws.StationView;

import pt.ulisboa.tecnico.sdis.ws.uddi.UDDINaming;
import pt.ulisboa.tecnico.sdis.ws.uddi.UDDINamingException;
import pt.ulisboa.tecnico.sdis.ws.uddi.UDDIRecord;

import javax.xml.ws.AsyncHandler;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Response;

/**
 * BinasManager class 
 * 
 * Class that have the methods used to get/Return Bina, beginning a station, querying all stations, etc.
 *
 */
public class BinasManager {
	/**
	 * UDDI server URL
	 */
	private String uddiURL = null;

	/**
	 * Station name
	 */
	private String stationTemplateName = null;

	// Singleton -------------------------------------------------------------

	private BinasManager() {
	}

	/**
	 * SingletonHolder is loaded on the first execution of Singleton.getInstance()
	 * or the first access to SingletonHolder.INSTANCE, not before.
	 */
	private static class SingletonHolder {
		private static final BinasManager INSTANCE = new BinasManager();
	}

	public static synchronized BinasManager getInstance() {
		return SingletonHolder.INSTANCE;
	}

	// Binas Logic ----------------------------------------------------------

	public User createUser(String email) throws UserAlreadyExistsException, InvalidEmailException {
		return UsersManager.getInstance().RegisterNewUser(email);
	}

	public User getUser(String email) throws UserNotFoundException {
		return UsersManager.getInstance().getUser(email);
	}
	
	public void rentBina(String stationId, String email) throws UserNotFoundException, InsufficientCreditsException, UserAlreadyHasBinaException, StationNotFoundException, NoBinaAvail_Exception {
		User user = getUser(email);
		int new_credit = 0;
		synchronized (user) {
			//validate user can rent
			user.validateCanRentBina();
			try {
				BalanceView view = quorumRead(email);
				new_credit = view.getCredit() -1;
				
			} catch (NoUserFound_Exception e) {
				System.err.println("Caught exception on rentBina: " + e);
			}
			
			//validate station can rent
			StationClient stationCli = getStation(stationId);
			stationCli.getBina();
			
			//apply rent action to user
			user.effectiveRent();
			try {
				quorumWrite(email,new_credit);
			} catch (NoUserFound_Exception | InvalidCredit_Exception e) {
				System.err.println("Caught exception on rentBina: " + e);
			}
		}
	}
	
	public void returnBina(String stationId, String email) throws UserNotFoundException, NoSlotAvail_Exception, UserHasNoBinaException, StationNotFoundException {
		User user = getUser(email);
		int new_credit = 0;
		synchronized (user) {
			//validate user can rent
			user.validateCanReturnBina();
			
			//validate station can rent
			StationClient stationCli = getStation(stationId);
			int prize = stationCli.returnBina();
			try {
				BalanceView view = quorumRead(email);
				new_credit = view.getCredit() + prize;
				
			} catch (NoUserFound_Exception e) {
				System.err.println("Caught exception on returnBina: " + e);
			}
			
			//apply rent action to user
			user.effectiveReturn(prize);
			try {
				quorumWrite(email,new_credit);
			} catch (NoUserFound_Exception | InvalidCredit_Exception e) {
				System.err.println("Caught exception on returnBina: " + e);
			}
		}		
	}

	public StationClient getStation(String stationId) throws StationNotFoundException {

		Collection<String> stations = this.getStations();
		String uddiUrl = BinasManager.getInstance().getUddiURL();
		
		for (String s : stations) {
			try {
				StationClient sc = new StationClient(uddiUrl, s);
				org.binas.station.ws.StationView sv = sc.getInfo();
				String idToCompare = sv.getId();
				if (idToCompare.equals(stationId)) {
					return sc;
				}
			} catch (StationClientException e) {
				continue;
			}
		}
		
		throw new StationNotFoundException();
	}
	
	
	// UDDI ------------------------------------------------------------------

	public void initUddiURL(String uddiURL) {
		setUddiURL(uddiURL);
	}

	public void initStationTemplateName(String stationTemplateName) {
		setStationTemplateName(stationTemplateName);
	}

	public String getUddiURL() {
		return uddiURL;
	}

	private void setUddiURL(String url) {
		uddiURL = url;
	}

	private void setStationTemplateName(String sn) {
		stationTemplateName = sn;
	}

	public String getStationTemplateName() {
		return stationTemplateName;
	}

	/**
	 * Get list of stations for a given query
	 * 
	 * @return List of stations
	 */
	public Collection<String> getStations() {
		Collection<UDDIRecord> records = null;
		Collection<String> stations = new ArrayList<String>();
		try {
			UDDINaming uddi = new UDDINaming(uddiURL);
			records = uddi.listRecords(stationTemplateName + "%");
			for (UDDIRecord u : records)
				stations.add(u.getOrgName());
		} catch (UDDINamingException e) {
		}
		return stations;
	}

	public void reset() {
		UsersManager.getInstance().reset();
	}

	public void init(int userInitialPoints) throws BadInitException {
		if(userInitialPoints < 0) {
			throw new BadInitException();
		}
		UsersManager.getInstance().init(userInitialPoints);
	}

	/**
	 * 
	 * Inits a Station with a determined ID, coordinates, capacity and returnPrize
	 * 
	 * @param stationId
	 * @param x
	 * @param y
	 * @param capacity
	 * @param returnPrize
	 * @throws BadInitException
	 * @throws StationNotFoundException
	 */
	public void testInitStation(String stationId, int x, int y, int capacity, int returnPrize) throws BadInitException, StationNotFoundException {
		//validate station can rent
		StationClient stationCli;
		try {
			stationCli = getStation(stationId);
			stationCli.testInit(x, y, capacity, returnPrize);
		} catch (BadInit_Exception e) {
			throw new BadInitException(e.getMessage());
		}
		
	}
	
	public BalanceView quorumRead(String email) throws StationNotFoundException, NoUserFound_Exception {
		
		Collection<String> stations = this.getStations();
		Response<GetBalanceResponse> response = null;
		String uddiUrl = BinasManager.getInstance().getUddiURL();
		BalanceView view = new BalanceView();
		int maxTag=-1;
		float Q = 0;
		int sts = 0;
		int contador = 0;
		int credit = 0; 
		
		for (String s : stations) {
			try {
				sts ++;
				StationClient sc = new StationClient(uddiUrl, s);
				response = sc.getBalanceAsync(email);
			} catch (StationClientException e) {
				continue;
			}
		}
		Q = Math.round((sts/2)+1);
		 ArrayList<BalanceView> list = new ArrayList<BalanceView>();
         while (contador < Q) {
        	if(response.isDone()) {
        		contador ++;
        		try {
					list.add(response.get().getBalanceInfo());
				} catch (InterruptedException | ExecutionException e) {
					System.err.println("Caught exception on quorumRead: " + e);
				}
        	} 
        	
            try {
				Thread.sleep(50 /* milliseconds */);
			} catch (InterruptedException e) {
				System.err.println("Caught exception on quorumRead: " + e);
			}
         }
         
         //verificar a maior tag
         for(int i=0; i < list.size() ; i++) {
        	if(list.get(i).getTag() > maxTag) {
        		maxTag = list.get(i).getTag();
        		credit = list.get(i).getCredit();
        	}
         }
        
		//view.setCredit(credit);
        try {
			view.setCredit(this.getUser(email).getCredit());
		} catch (UserNotFoundException e) {
			System.err.println("Caught exception on quorumRead: " + e);
		}
        view.setTag(maxTag); 
         
		return view;
	}
	
	public void quorumWrite(String email, int credit) throws StationNotFoundException, NoUserFound_Exception, InvalidCredit_Exception {
		
		Collection<String> stations = this.getStations();
		Response<SetBalanceResponse> response = null;
		String uddiUrl = BinasManager.getInstance().getUddiURL();
		int contador = 0;
		float Q = 0;
		int sts = 0;
		
		BalanceView view = quorumRead(email);
		
		int newTag = view.getTag() + 1;
		
		for (String s : stations) {
			try {
				sts ++;
				StationClient sc = new StationClient(uddiUrl, s);
				response = sc.setBalanceAsync(email,credit,newTag);
				System.out.println("USER: " + email+ " Balance: "+ credit + " credits TAG: "+ newTag);
			} catch (StationClientException e) {
				continue;
			}
		}
		Q = Math.round((sts/2)+1);
		while (contador < Q) {
	        if(response.isDone()) {
	        	contador ++;
	        }
	        try {
				Thread.sleep(50 /* milliseconds */);
			} catch (InterruptedException e) {
				System.err.println("Caught exception on quorumWrite: " + e);
			}
	    }
	}	
}
