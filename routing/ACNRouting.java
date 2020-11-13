package routing;

import java.util.*;
import util.Tuple;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;

public class ACNRouting extends ActiveRouter {

	//constants
	private int MAX_QUEUE_SIZE = 50;
	protected double INITIAL_TRUST_VALUE = 1.0;
	protected double INCREMENT_TRUST_VALUE = 1.0;
	protected double encounterConstant = 1.0;
	protected double trustConstant = 1.0;
	 
	//variables

	private Map<DTNHost, Map<DTNHost, Double>> trust;
	protected Map<DTNHost, Double> meetings;
	private int nrofSamplesIET;
	private double meanIET;
	
	//to store the last MAX_QUEUE_SIZE encounters only
	Vector<DTNHost> LastEncounters = new Vector<DTNHost>();

	protected Map<DTNHost, Integer> encounters;
	private int nrofSamplesENC;
	private double meanENC;
	private int nrofTotENC;

	//constructrs
	public ACNRouting(Settings settings) {
		super(settings);
		
		initMeetings();
	}
	
	protected ACNRouting(ACNRouting r) {
		super(r);
		initTrust();
		initMeetings();
	}

	private void initTrust() {
		// this.trust = new HashMap<DTNHost, (new HashMap<DTNHost, Double>())>();
		// Map<DTNHost, Double> temptrustmap = new HashMap<DTNHost, Double>();
		this.trust = new HashMap<DTNHost, Map<DTNHost, Double>>();
	}

	private void initMeetings() {
		this.meetings = new HashMap<DTNHost, Double>();
		this.encounters = new HashMap<DTNHost, Integer>();
		this.meanIET = 0;
		this.nrofSamplesIET = 0;
		this.meanENC = 0;
		this.nrofSamplesENC = 0;
		this.nrofTotENC = 0;
	}

	// if changed connection
	@Override
	public void changedConnection(Connection con) {
		super.changedConnection(con);
		
		if (con.isUp()) {
			DTNHost otherHost = con.getOtherNode(getHost());
			MessageRouter mRouter = otherHost.getRouter();
			this.changeMeetingStats(otherHost); //for current host
			// otherHost.changeMeetingStats(getHost()); // for dest host
		}
	}

	protected void changeMeetingStats(DTNHost host) {		
		double currentTime = SimClock.getTime();
		if (meetings.containsKey(host)) {
			double timeDiff = currentTime - meetings.get(host);
			nrofSamplesIET++;
			meanIET = (((double)nrofSamplesIET -1) / (double)nrofSamplesIET) * meanIET
			+ (1 / (double)nrofSamplesIET) * timeDiff;
			meetings.put(host, currentTime);
		} else {
			meetings.put(host,currentTime);
		}	

		nrofTotENC++; // the number of encounter
		LastEncounters.add(host);
		if(LastEncounters.size() > MAX_QUEUE_SIZE) {
			DTNHost temphost = LastEncounters.get(0);
			int val = encounters.get(temphost);
			val--;
			encounters.put(temphost,val);
		}
		if (encounters.containsKey(host)) {
			// int encounterNro = nrofTotENC - encounters.get(host);
			// nrofSamplesENC++;
			// meanENC = (((double)nrofSamplesENC -1) / (double)nrofSamplesENC) * meanENC
			// + (1 / (double)nrofSamplesENC) * (double)encounterNro;
			// encounters.put(host,nrofTotENC);
			// return true;
			int val = encounters.get(host);
			val++;
			encounters.put(host,val);

		} else {
			encounters.put(host,1);
		}		
	}

	// @Override
	// public Message messageTransferred(String id, DTNHost from) {
	// 	this.costsForMessages = null; // new message -> invalidate costs
	// 	Message m = super.messageTransferred(id, from);
	// 	if (isDeliveredMessage(m)) {
	// 		this.ackedMessageIds.add(id);
	// 	}
	// 	return m;
	// }
	
	@Override
	protected void transferDone(Connection con) {
		Message m = con.getMessage();
		// if (m.getTo() == con.getOtherNode(getHost())) { 
		// 	this.ackedMessageIds.add(m.getId()); // yes, add to ACKed messages
		// 	this.deleteMessage(m.getId(), false); // delete from buffer
		// }

		List<DTNHost> lsthops = new ArrayList<DTNHost>();
		lsthops = m.getHops();
		if(lsthops.size() < 2){
			return;
		}

		DTNHost tempHost = con.getOtherNode(lsthops.get(lsthops.size() - 2));
		DTNHost tempHost1 = con.getOtherNode(lsthops.get(lsthops.size() - 1));
		try {
			// tempHost.deleteMessage(m.getId(), false); // delete from buffer
		}
		catch(Exception e) {}
		double temptrustvalue = INITIAL_TRUST_VALUE;
		// if (meetings.containsKey(tmphost1)) {
		// 	temptrust = tmphost.trust.get(tmphost1);
		// 	temptrust++;
		// }
		// tmphost.trust.put(tmphost1, temptrust);
		Map<DTNHost, Double> temptrustmap = new HashMap<DTNHost, Double>();
		if(trust.containsKey(tempHost)) {
			temptrustmap = trust.get(tempHost);
			if(temptrustmap.containsKey(tempHost1)) {
				temptrustvalue = temptrustmap.get(tempHost1);
				temptrustvalue += INCREMENT_TRUST_VALUE;
			}
			//else it is inital value
			
			temptrustmap.put(tempHost1, temptrustvalue);
			trust.put(tempHost, temptrustmap);
		}
		else {
			temptrustmap.put(tempHost1, temptrustvalue);
			trust.put(tempHost, temptrustmap);
		}
	}

	//update function
	@Override
	public void update() {
		super.update();
		if (!canStartTransfer() ||isTransferring()) {
			return; // nothing to transfer or is currently transferring 
		}
		
		if (exchangeDeliverableMessages() != null) {
			return;
		}
		
		tryOtherMessages();	
	}

	private Tuple<Message, Connection> tryOtherMessages() {
				List<Tuple<Message, Connection>> messages = 
			new ArrayList<Tuple<Message, Connection>>(); 
	
		Collection<Message> msgCollection = getMessageCollection();
		
		/* for all connected hosts collect all messages that have a higher
		   probability of delivery by the other host */
		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			ACNRouting othRouter = (ACNRouting)other.getRouter();
			
			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}
			
			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId()) ||
						m.getHops().contains(other)) {
					continue; 
				}
				messages.add(new Tuple<Message, Connection>(m,con));
			}			
		}
		
		if (messages.size() == 0) {
			return null;
		}
		
		// sort the message-connection tuples
		Collections.sort(messages, new TupleComparator());
		return tryMessagesForConnected(messages);	// try to send messages
	}

	private class TupleComparator implements Comparator 
		<Tuple<Message, Connection>> {

		public int compare(Tuple<Message, Connection> tuple1,
				Tuple<Message, Connection> tuple2) {
			// // delivery probability of tuple1's message with tuple1's connection
			// double p1 = ((ProphetRouter)tuple1.getValue().
			// 		getOtherNode(getHost()).getRouter()).getPredFor(
			// 		tuple1.getKey().getTo());
			// // -"- tuple2...
			// double p2 = ((ProphetRouter)tuple2.getValue().
			// 		getOtherNode(getHost()).getRouter()).getPredFor(
			// 		tuple2.getKey().getTo());

			DTNHost from1 = tuple1.getValue().getOtherNode(getHost());
			DTNHost from2 = tuple2.getValue().getOtherNode(getHost());
			double trust1=0.0,trust2=0.0;
			try{
				trust1 = trust.get(getHost()).get(from1);
			}
			catch(Exception e) {
				trust1 = INITIAL_TRUST_VALUE;
			}
			try {
				trust2 = trust.get(getHost()).get(from2);
			}
			catch(Exception e) {
				trust2 = INITIAL_TRUST_VALUE;
			}
			double p1 = encounterConstant*(encounters.get(from1) + meetings.get(from1)) + trustConstant*(trust1);
			double p2 = encounterConstant*(encounters.get(from2) + meetings.get(from2)) + trustConstant*(trust2);

			// bigger probability should come first
			// if (p2-p1 == 0) {
			// 	 //equal probabilities -> let queue mode decide 
			// 	return compareByQueueMode(tuple1.getKey(), tuple2.getKey());
			// }
			if (p2-p1 < 0) {
				return -1;
			}
			else {
				return 1;
			}
		}
	}


	//last override
	@Override
	public MessageRouter replicate() {
		ACNRouting r = new ACNRouting(this);
		return r;
	}

}