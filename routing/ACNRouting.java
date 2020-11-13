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
	//variables
	private Map<DTNHost, Double> trust;

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

		initMeetings();
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
			
			double p1 = encounters.get(from1) + meetings.get(from1);
			double p2 = encounters.get(from2)  + meetings.get(from2);

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