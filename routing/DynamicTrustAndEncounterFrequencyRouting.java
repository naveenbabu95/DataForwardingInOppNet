package routing;

import java.util.*;
import util.Tuple;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;

public class DynamicTrustAndEncounterFrequencyRouting extends ActiveRouter {

	//constants
	private int MAX_QUEUE_SIZE = 50;
	protected double AVERAGE_HOP_COUNT = 10.0;
	protected double INITIAL_TRUST_VALUE = 1.0;
	protected double INCREMENT_TRUST_VALUE = 1.0;
	protected double FINAL_DESTINATION_TRUST_VALUE_INCREMENT = 2.0;
	protected double encounterConstant = 1.0;
	protected double trustConstant = 1.0;
	 
	//variables

	private Map<DTNHost, Map<DTNHost, Double>> trust;
	protected Map<DTNHost, Map<DTNHost, Double>> meetingsStartTime;
	protected Map<DTNHost, Map<DTNHost, Double>> meetingsDuration;
	private int nrofSamplesIET;
	private double meanIET;
	
	//to store the last MAX_QUEUE_SIZE encounters only
	Vector<DTNHost> LastEncounters = new Vector<DTNHost>();

	protected Map<DTNHost, Map<DTNHost, Integer>> encounters;
	private int nrofSamplesENC;
	private double meanENC;
	private int nrofTotENC;

	//constructrs
	public DynamicTrustAndEncounterFrequencyRouting(Settings settings) {
		super(settings);
		initTrust();
		initMeetings();
	}
	
	protected DynamicTrustAndEncounterFrequencyRouting(DynamicTrustAndEncounterFrequencyRouting r) {
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
		this.meetingsStartTime = new HashMap<DTNHost, Map<DTNHost, Double>>();
		this.meetingsDuration = new HashMap<DTNHost, Map<DTNHost, Double>>();
		this.encounters = new HashMap<DTNHost, Map<DTNHost, Integer>>();
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
		DTNHost toNode = con.getOtherNode(getHost());
		DTNHost fromNode = getHost();
		if (con.isUp()) {
			// MessageRouter mRouter = otherHost.getRouter();
			this.updateEncounterStats(toNode); //for current host
			this.updateMeetingStartTime(fromNode, toNode);
			// this.updateMeetingStartTime(con.toNode, con.fromNode);
			// otherHost.changeMeetingStats(getHost()); // for dest host
		}
		else {

			this.updateMeetingDuration(fromNode, toNode);
		}
	}

	private void updateMeetingStartTime(DTNHost from, DTNHost to) {
		double currentTime = SimClock.getTime();
		Map<DTNHost, Double> tempMeetingsStartTime = new HashMap<DTNHost, Double>();
		if(meetingsStartTime.containsKey(from)) {
			tempMeetingsStartTime = meetingsStartTime.get(from);
		}
		tempMeetingsStartTime.put(to, currentTime);
		meetingsStartTime.put(from, tempMeetingsStartTime);
	}

	private void updateMeetingDuration(DTNHost from, DTNHost to){
		double currentTime = SimClock.getTime();
		Map<DTNHost, Double> tempMapHost;
		if(!meetingsStartTime.containsKey(getHost())) return;
		// if(meetingsstarttime.containsKey(getHost())) {
		tempMapHost = meetingsStartTime.get(getHost());

		// } 
		double startTime = tempMapHost.get(to);
		Map<DTNHost, Double> tempMapHost1 = new HashMap<DTNHost, Double>();
		double tempDuration = 0.0;
		if(meetingsDuration.containsKey(getHost())) {
			tempMapHost1 = meetingsDuration.get(getHost());
			if(tempMapHost1.containsKey(to))
				tempDuration = tempMapHost1.get(to);
		}
		tempDuration += (currentTime - startTime);
		tempMapHost1.put(to, tempDuration);
		meetingsDuration.put(from, tempMapHost1);
	}

	private void updateEncounterStats(DTNHost host) {		
		// double currentTime = SimClock.getTime();
		// if (meetings.containsKey(host)) {
		// 	double timeDiff = currentTime - meetings.get(host);
		// 	nrofSamplesIET++;
		// 	meanIET = (((double)nrofSamplesIET -1) / (double)nrofSamplesIET) * meanIET
		// 	+ (1 / (double)nrofSamplesIET) * timeDiff;
		// 	meetings.put(host, currentTime);
		// } else {
		// 	meetings.put(host,currentTime);
		// }	

		nrofTotENC++; // the number of encounter
		LastEncounters.add(host);
		if(LastEncounters.size() > MAX_QUEUE_SIZE) {
			DTNHost tempdtnhost = LastEncounters.get(0);
			Map<DTNHost, Integer> tempMapHost;
			tempMapHost = encounters.get(getHost());
			int val = tempMapHost.get(tempdtnhost);
			val--;
			tempMapHost.put(tempdtnhost,val);
			encounters.put(getHost(), tempMapHost);
		}
		if (encounters.containsKey(getHost())) {
			// int encounterNro = nrofTotENC - encounters.get(host);
			// nrofSamplesENC++;
			// meanENC = (((double)nrofSamplesENC -1) / (double)nrofSamplesENC) * meanENC
			// + (1 / (double)nrofSamplesENC) * (double)encounterNro;
			// encounters.put(host,nrofTotENC);
			// return true;
			Map<DTNHost, Integer> tempMapHost;
			tempMapHost = encounters.get(getHost());
			int val = 0;
			if(tempMapHost.containsKey(host))
				val = tempMapHost.get(host);
			val++;
			tempMapHost.put(host, val);
			encounters.put(getHost(), tempMapHost);

		}
		else {
			Map<DTNHost, Integer> tempMapHost = new HashMap<DTNHost, Integer>();;
			tempMapHost.put(host, 1);
			encounters.put(getHost(), tempMapHost);
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
		DTNHost recipient = con.getOtherNode(getHost());
		List<DTNHost> lsthops = new ArrayList<DTNHost>();
		lsthops = m.getHops();

		// if (m.getTo() == recipient) { 
		// 	for (DTNHost host1 : lsthops) {
		// 		Map<DTNHost, Double> temptrustmap = new HashMap<DTNHost, Double>();
		// 		if(trust.containsKey(host1)) {
		// 			temptrustmap = trust.get(host1);
		// 		}
		// 		for(DTNHost host2: lsthops) {
		// 			double temptrustvalue = INITIAL_TRUST_VALUE;
		// 			if(temptrustmap.containsKey(host2)) {
		// 				temptrustvalue = temptrustmap.get(host2);
		// 			}
		// 			// temptrustvalue += FINAL_DESTINATION_TRUST_VALUE_INCREMENT;
					
		// 			//to give lesser hop count more advantage
		// 			temptrustvalue += (AVERAGE_HOP_COUNT/lsthops.size())*FINAL_DESTINATION_TRUST_VALUE_INCREMENT;
		// 			temptrustmap.put(host2, temptrustvalue);
		// 		}
		// 		trust.put(host1, temptrustmap);
		// 	}
		// 	return;
		// }

		// Destn based trust
		if (m.getTo() == recipient) { 
			DTNHost host1 = getHost();
			Map<DTNHost, Double> temptrustmap = new HashMap<DTNHost, Double>();
			if(trust.containsKey(host1)) {
				temptrustmap = trust.get(host1);
			}
			for(DTNHost host2: lsthops) {
				double temptrustvalue = INITIAL_TRUST_VALUE;
				if(temptrustmap.containsKey(host2)) {
					temptrustvalue = temptrustmap.get(host2);
				}
				// temptrustvalue += FINAL_DESTINATION_TRUST_VALUE_INCREMENT;
				
				//to give lesser hop count more advantage
				temptrustvalue += (AVERAGE_HOP_COUNT/lsthops.size())*FINAL_DESTINATION_TRUST_VALUE_INCREMENT;
				temptrustmap.put(host2, temptrustvalue);
			}
			trust.put(host1, temptrustmap);
			return;
		}

		if(lsthops.size() < 2){
			return;
		}

		//2-Hop

		DTNHost tempHost = (lsthops.get(lsthops.size() - 2));
		DTNHost tempHost1 = (lsthops.get(lsthops.size() - 1));
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
			DynamicTrustAndEncounterFrequencyRouting othRouter = (DynamicTrustAndEncounterFrequencyRouting)other.getRouter();
			
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
			double trust1=0.0,trust2=0.0, encounters1=0.0, encounters2=0.0, duration1=0.0, duration2=0.0;
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
			try {
				Map<DTNHost, Integer> tempHost;
				Map<DTNHost, Double> tempHost1;
				tempHost = encounters.get(getHost());
				tempHost1 = meetingsDuration.get(getHost());
				encounters1 = tempHost.get(from1);
				duration1 = tempHost1.get(from1);
			}
			catch(Exception e) {}
			try {
				Map<DTNHost, Integer> tempHost;
				Map<DTNHost, Double> tempHost1;
				tempHost = encounters.get(getHost());
				tempHost1 = meetingsDuration.get(getHost());
				encounters2 = tempHost.get(from2);
				duration2 = tempHost1.get(from2);
			}
			catch(Exception e) {}
			// double p1 = encounterConstant*(encounters.get(from1) + meetings.get(from1)) + trustConstant*(trust1);
			// double p2 = encounterConstant*(encounters.get(from2) + meetings.get(from2)) + trustConstant*(trust2);
			double p1 = encounterConstant*(encounters1*duration1) + trustConstant*(trust1);
			double p2 = encounterConstant*(encounters2*duration2) + trustConstant*(trust2);

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
		DynamicTrustAndEncounterFrequencyRouting r = new DynamicTrustAndEncounterFrequencyRouting(this);
		return r;
	}

}