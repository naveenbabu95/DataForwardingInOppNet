package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import routing.maxprop.MaxPropDijkstra;
import routing.maxprop.MeetingProbabilitySet;
import routing.util.RoutingInfo;
import util.Tuple;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;


public class TestRoutingOppNet extends ActiveRouter {
	private MeetingProbabilitySet probs;
	private Map<Integer, MeetingProbabilitySet> allProbs;
	private MaxPropDijkstra dijkstra;	
	private Set<String> ackedMessageIds;
	private Map<Integer, Double> costsForMessages;
	private DTNHost lastCostFrom;

	public static int BYTES_TRANSFERRED_AVG_SAMPLES = 10;
	private int[] avgSamples;
	private int nextSampleIndex = 0;
	private int avgTransferredBytes = 0;
	
	public static final String MAXPROP_NS = "TestRoutingOppNet";

	public static final String TIME_SCALE_S ="timeScale";

	private double alpha;

	public static final double DEFAULT_ALPHA = 1.0;

	private int timescale;

	private Map<DTNHost, Double> meetings;
	private int nrofSamplesIET;
	private double meanIET;
	
	private Map<DTNHost, Integer> encounters;
	private int nrofSamplesENC;
	private double meanENC;
	private int nrofTotENC;
	
	public TestRoutingOppNet(Settings settings) {
		super(settings);
		Settings maxPropSettings = new Settings(MAXPROP_NS);
		alpha = DEFAULT_ALPHA;
		timescale = maxPropSettings.getInt(TIME_SCALE_S);
		initMeetings();
	}
	
	protected TestRoutingOppNet(TestRoutingOppNet r) {
		super(r);
		this.alpha = r.alpha;
		this.timescale = r.timescale;
		this.probs = new MeetingProbabilitySet(
				MeetingProbabilitySet.INFINITE_SET_SIZE, this.alpha);
		this.allProbs = new HashMap<Integer, MeetingProbabilitySet>();
		this.dijkstra = new MaxPropDijkstra(this.allProbs);
		this.ackedMessageIds = new HashSet<String>();
		this.avgSamples = new int[BYTES_TRANSFERRED_AVG_SAMPLES];
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
	
	@Override
	public void changedConnection(Connection con) {
		super.changedConnection(con);
		
		if (con.isUp()) { // new connection
			this.costsForMessages = null; // invalidate old cost estimates
			
			if (con.isInitiator(getHost())) {
				DTNHost otherHost = con.getOtherNode(getHost());
				MessageRouter mRouter = otherHost.getRouter();

				assert mRouter instanceof TestRoutingOppNet : "MaxProp only works "+ 
				" with other routers of same type";
				TestRoutingOppNet otherRouter = (TestRoutingOppNet)mRouter;
				
				if (this.updateEstimators(otherHost)) {
					this.updateParam();					
				}
				if (otherRouter.updateEstimators(getHost())) {
					otherRouter.updateParam();
				}
				
				this.ackedMessageIds.addAll(otherRouter.ackedMessageIds);
				otherRouter.ackedMessageIds.addAll(this.ackedMessageIds);
				deleteAckedMessages();
				otherRouter.deleteAckedMessages();
				
				probs.updateMeetingProbFor(otherHost.getAddress());
				otherRouter.probs.updateMeetingProbFor(getHost().getAddress());
				
				this.updateTransitiveProbs(otherRouter.allProbs);
				otherRouter.updateTransitiveProbs(this.allProbs);
				this.allProbs.put(otherHost.getAddress(),
						otherRouter.probs.replicate());
				otherRouter.allProbs.put(getHost().getAddress(),
						this.probs.replicate());
			}
		}
		else {
			updateTransferredBytesAvg(con.getTotalBytesTransferred());
		}
	}

	private void updateTransitiveProbs(Map<Integer, MeetingProbabilitySet> p) {
		for (Map.Entry<Integer, MeetingProbabilitySet> e : p.entrySet()) {
			MeetingProbabilitySet myMps = this.allProbs.get(e.getKey()); 
			if (myMps == null || 
				e.getValue().getLastUpdateTime() > myMps.getLastUpdateTime() ) {
				this.allProbs.put(e.getKey(), e.getValue().replicate());
			}
		}
	}
	
	protected boolean updateEstimators(DTNHost host) {		
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
		if (encounters.containsKey(host)) {
			int encounterNro = nrofTotENC - encounters.get(host);
			nrofSamplesENC++;
			meanENC = (((double)nrofSamplesENC -1) / (double)nrofSamplesENC) * meanENC
			+ (1 / (double)nrofSamplesENC) * (double)encounterNro;
			encounters.put(host,nrofTotENC);
			return true;
		} else {
			encounters.put(host,nrofTotENC);
			return false;
		}		
	}

	protected void updateParam()
	{
		double err = .01;
		double ntarg = Math.ceil(timescale/meanIET);
		double ee = 1;
		double alphadiff = .1;
		int ob = 0;
		double fstable;
		double fnzero;
		double fnone;
		double eezero;
		double eeone;
		double A;
		
		if (meanIET > (double)timescale) {
			System.out.printf("meanIET %f > %d timescale\n",meanIET,timescale);
			return;
		}
			
		if (meanIET == 0) {
			System.out.printf("Mean IET == 0\n");
			return;
		}			
		
		if (meanENC == 0) {
			System.out.printf("Mean ENC == 0\n");
			return;
		}			
		
		while (ee != err) {
			A = Math.pow(1+alpha,meanENC+1);
			fstable = alpha/(A-1);
			fnzero = (alpha/A)*(1-Math.pow(A,-ntarg))/(1-1/A);
			fnone  = fnzero + 1/(Math.pow(A,ntarg));
			eezero = Math.abs(fnzero-fstable);
			eeone  = Math.abs(fnone -fstable);
			ee = Math.max(eezero,eeone);

			if (ee > err ) {
				if (ob == 2) {
					alphadiff = alphadiff / 2.0;
				}
				ob = 1;
				alpha = alpha+alphadiff;
			} else {
				if (ee < (err-err*0.001)) {
					if (ob == 1) {
						alphadiff = alphadiff / 2.0;
					}
					ob = 2;
					alpha = alpha - alphadiff;

					// double precision floating makes problems...
					if ((alpha <= 0) | (((1 + alpha) - 1) == 0)) {
						alpha = alphadiff;
						alphadiff = alphadiff / 2.0;
						ob = 0;
					}
				} else {
					ee = err;
				}
			}
		}
		probs.setAlpha(alpha);
	}

	private void deleteAckedMessages() {
		for (String id : this.ackedMessageIds) {
			if (this.hasMessage(id) && !isSending(id)) {
				this.deleteMessage(id, false);
			}
		}
	}
	
	@Override
	public Message messageTransferred(String id, DTNHost from) {
		this.costsForMessages = null; // new message -> invalidate costs
		Message m = super.messageTransferred(id, from);
		if (isDeliveredMessage(m)) {
			this.ackedMessageIds.add(id);
		}
		return m;
	}
	
	@Override
	protected void transferDone(Connection con) {
		Message m = con.getMessage();
		if (m.getTo() == con.getOtherNode(getHost())) { 
			this.ackedMessageIds.add(m.getId()); // yes, add to ACKed messages
			this.deleteMessage(m.getId(), false); // delete from buffer
		}
	}

	private void updateTransferredBytesAvg(int newValue) {
		int realCount = 0;
		int sum = 0;
		
		this.avgSamples[this.nextSampleIndex++] = newValue;
		if(this.nextSampleIndex >= BYTES_TRANSFERRED_AVG_SAMPLES) {
			this.nextSampleIndex = 0;
		}
		
		for (int i=0; i < BYTES_TRANSFERRED_AVG_SAMPLES; i++) {
			if (this.avgSamples[i] > 0) { // only values above zero count
				realCount++;
				sum += this.avgSamples[i];
			}
		}
		
		if (realCount > 0) {
			this.avgTransferredBytes = sum / realCount;
		}
		else { // no samples or all samples are zero
			this.avgTransferredBytes = 0;
		}
	}
	
	protected Message getNextMessageToRemove(boolean excludeMsgBeingSent) {
		Collection<Message> messages = this.getMessageCollection();
		List<Message> validMessages = new ArrayList<Message>();

		for (Message m : messages) {	
			if (excludeMsgBeingSent && isSending(m.getId())) {
				continue; // skip the message(s) that router is sending
			}
			validMessages.add(m);
		}
		
		Collections.sort(validMessages, 
				new MaxPropComparator(this.calcThreshold())); 
		
		return validMessages.get(validMessages.size()-1); // return last message
	}
	
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
	
	public double getCost(DTNHost from, DTNHost to) {
		if (this.costsForMessages == null || lastCostFrom != from) {
			this.allProbs.put(getHost().getAddress(), this.probs);
			int fromIndex = from.getAddress();
			

			Set<Integer> toSet = new HashSet<Integer>();
			for (Message m : getMessageCollection()) {
				toSet.add(m.getTo().getAddress());
			}
						
			this.costsForMessages = dijkstra.getCosts(fromIndex, toSet);
			this.lastCostFrom = from; // store source host for caching checks
		}
		
		if (costsForMessages.containsKey(to.getAddress())) {
			return costsForMessages.get(to.getAddress());
		}
		else {
			return Double.MAX_VALUE;
		}
	}
	
	private Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages = 
			new ArrayList<Tuple<Message, Connection>>(); 
	
		Collection<Message> msgCollection = getMessageCollection();
		
		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			TestRoutingOppNet othRouter = (TestRoutingOppNet)other.getRouter();
			
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
		
		Collections.sort(messages, new MaxPropTupleComparator(calcThreshold()));
		return tryMessagesForConnected(messages);	
	}

	public int calcThreshold() {
		int b = this.getBufferSize();
		int x = this.avgTransferredBytes;
		int p;

		if (x == 0) {
			/* can't calc the threshold because there's no transfer data */
			return 0;
		}
		
		if (x < b/2) {
			p = x;
		}
		else if (b/2 <= x && x < b) {
			p = Math.min(x, b-x);
		}
		else {
			return 0; // no need for the threshold 
		}
		
		ArrayList<Message> msgs = new ArrayList<Message>();
		msgs.addAll(getMessageCollection());
		if (msgs.size() == 0) {
			return 0; // no messages -> no need for threshold
		}
		Comparator<Message> hopCountComparator = new Comparator<Message>() {
			public int compare(Message m1, Message m2) {
				return m1.getHopCount() - m2.getHopCount();
			}
		};
		Collections.sort(msgs, hopCountComparator);

		int i=0;
		for (int n=msgs.size(); i<n && p>0; i++) {
			p -= msgs.get(i).getSize();
		}
		
		i--; // the last round moved i one index too far 
		
		return msgs.get(i).getHopCount() + 1;
	}
	
	private class MaxPropComparator implements Comparator<Message> {
		private int threshold;
		private DTNHost from1;
		private DTNHost from2;
		public MaxPropComparator(int treshold) {
			this.threshold = treshold;
			this.from1 = this.from2 = getHost();
		}

		public MaxPropComparator(int treshold, DTNHost from1, DTNHost from2) {
			this.threshold = treshold;
			this.from1 = from1;
			this.from2 = from2;
		}

		public int compare(Message msg1, Message msg2) {
			double p1, p2;
			int hopc1 = msg1.getHopCount();
			int hopc2 = msg2.getHopCount();

			if (msg1 == msg2) {
				return 0;
			}
			
			if (hopc1 < threshold && hopc2 >= threshold) {
				return -1; // message1 should be first
			}
			else if (hopc2 < threshold && hopc1 >= threshold) {
				return 1; // message2 -"-
			}

			if (hopc1 < threshold && hopc2 < threshold) {
				return hopc1 - hopc2;
			}
			
			p1 = getCost(from1, msg1.getTo());
			p2 = getCost(from2, msg2.getTo());
			
			if (p1-p2 == 0) {
				if (hopc1 == hopc2) {
					return compareByQueueMode(msg1, msg2);
				}
				else {
					return hopc1 - hopc2;	
				}
			}
			else if (p1-p2 < 0) {
				return -1; // msg1 had the smaller cost
			}
			else {
				return 1; // msg2 had the smaller cost
			}
		}		
	}
	
	private class MaxPropTupleComparator 
			implements Comparator <Tuple<Message, Connection>>  {
		private int threshold;
		
		public MaxPropTupleComparator(int threshold) {
			this.threshold = threshold;
		}

		public int compare(Tuple<Message, Connection> tuple1,
				Tuple<Message, Connection> tuple2) {
			MaxPropComparator comp;
			DTNHost from1 = tuple1.getValue().getOtherNode(getHost());
			DTNHost from2 = tuple2.getValue().getOtherNode(getHost());
			
			comp = new MaxPropComparator(threshold, from1, from2);
			return comp.compare(tuple1.getKey(), tuple2.getKey());
		}
	}
	
	
	@Override
	public RoutingInfo getRoutingInfo() {
		RoutingInfo top = super.getRoutingInfo();
		RoutingInfo ri = new RoutingInfo(probs.getAllProbs().size() + 
				" meeting probabilities");
		
		for (Map.Entry<Integer, Double> e : probs.getAllProbs().entrySet()) {
			Integer host = e.getKey();
			Double value = e.getValue();			
			ri.addMoreInfo(new RoutingInfo(String.format("host %d : %.6f", 
					host, value)));
		}
			
		ri.addMoreInfo(new RoutingInfo(String.format("meanIET: %f\t from %d samples",meanIET,nrofSamplesIET)));
		ri.addMoreInfo(new RoutingInfo(String.format("meanENC: %f\t from %d samples",meanENC,nrofSamplesENC)));
		ri.addMoreInfo(new RoutingInfo(String.format("current alpha: %f",alpha)));
		
		top.addMoreInfo(ri);
		top.addMoreInfo(new RoutingInfo("Avg transferred bytes: " + 
				this.avgTransferredBytes));

		return top;
	}
	
	@Override
	public MessageRouter replicate() {
		TestRoutingOppNet r = new TestRoutingOppNet(this);
		return r;
	}
}