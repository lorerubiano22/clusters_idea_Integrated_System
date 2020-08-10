import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;



/**
 * @author Lorena
 *
 */
public class Algorithm {
	private final Test test;
	private final Inputs input;
	private WalkingRoutes subroutes;
	private DrivingRoutes routes;
	private  ArrayList<Couple> subJobsList= new ArrayList<Couple>();




	public Algorithm(Test t, Inputs i, Random r) {
		test = t;
		input = i;
		subroutes = new WalkingRoutes(input, r, t, i.getNodes()); // stage 1: Creation of walking routes
		updateListJobs();// jobs couple - class SubJobs
		routes = new DrivingRoutes(input, r, t,subJobsList); // stage 2: Creation of driving routes
		routes.generateAfeasibleSolution();
		Interaction stages= new Interaction(routes,subJobsList, input, r, t);// Iteration between stage 1 und stage 2: from the current walking routes split and define new ones
		routes= stages.getBestRoutes();
		subroutes= stages.getBestWalkingRoutes();
	}







	private void updateListJobs() {
		// stage 0: set the jobs which are not in a walking route
		ArrayList<Couple> clientJobs= createClientJobs(); // TO DO
		ArrayList<Couple> patientJobs= createPatientsJobs();
		// creating the list of subJobs <- each subjob could be also considered as a stop
		creatingSubjobsList(clientJobs,patientJobs);
	}

	private void creatingSubjobsList(ArrayList<Couple> clientJobs, ArrayList<Couple> patientJobs) {
		int i=-1;
		for(Couple j:clientJobs) {
			i++;
			System.out.println(j.toString());
			j.setIdCouple(i);
			subJobsList.add(j);

		}

		for(Couple j:patientJobs) {
			i++;
			System.out.println(j.toString());
			j.setIdCouple(i);
			subJobsList.add(j);
		}	

	}



	private ArrayList<Couple> createPatientsJobs() {
		// Los pacientes estan vinculados con el centro médico // 1
		ArrayList<Couple> coupleFromPatientsRequest= new ArrayList<Couple>();
		for(Jobs j: input.getpatients()) {

			// patient home -----going ----> Medical centre
			//0. creation of couple
			Couple pairPatientMedicalCentre= creatingCouplePatientHomeToMedicalCentre(j); 
			// 1. fixing time windows
			computingTimeWindowPatientMedicalCentreSubJob(pairPatientMedicalCentre,j);
			// 2. fixing number of people involved
			settingPeopleInSubJob(pairPatientMedicalCentre, +1,-2); //- drop-off #personas + pick up #personas
			// 3. checking information
			System.out.println(pairPatientMedicalCentre.toString());
			// 4. adding couple
			coupleFromPatientsRequest.add(pairPatientMedicalCentre);

			// patient home <-----returning ---- Medical centre  - copying for returning patient to patients home
			//0. creation of couple
			Couple pairMedicalCentrePatient=creatingCoupleMedicalCentreToPatientHome(j); 
			// 1. fixing time windows
			computingTimeWindowMedicalCentrePatientSubJob(pairMedicalCentrePatient,j);
			// 2. fixing number of people involved
			settingPeopleInSubJob(pairPatientMedicalCentre, +2,-1); //- drop-off #personas + pick up #personas
			// 3. checking information
			System.out.println(pairMedicalCentrePatient.toString());
			// 4. adding couple
			coupleFromPatientsRequest.add(pairMedicalCentrePatient);
		}
		return coupleFromPatientsRequest;
	}







	private void computingTimeWindowMedicalCentrePatientSubJob(Couple pairMedicalCentrePatient, Jobs j) {
		// Time window
		// 1. Present Job: pick patient and paramedic at medical centre
		// required time before the service starts
		double treatmentDuration=j.getReqTime();
		pairMedicalCentrePatient.getPresent().setStartTime(j.getStartTime()+(int)treatmentDuration); // earliest
		pairMedicalCentrePatient.getPresent().setEndTime(j.getStartTime()+(int)treatmentDuration+test.getCumulativeWaitingTime()); // latest
		// Service time: start time and duration service
		pairMedicalCentrePatient.getPresent().setStartServiceTime(pairMedicalCentrePatient.getPresent().getEndTime()); // start time <- latest time
		pairMedicalCentrePatient.getPresent().setserviceTime(test.getloadTimePatient()); // duration service

		// 2. Future Job: drop patient up time is set assuming a direct connection from patient home to medical centre
		// required time before the service starts
		double dHomeMedicalCentrePatient= input.getCarCost().getCost(pairMedicalCentrePatient.getPresent().getId(), pairMedicalCentrePatient.getFuture().getId());	
		double dropOffTime= pairMedicalCentrePatient.getPresent().getstartServiceTime()+(int)pairMedicalCentrePatient.getPresent().getReqTime()+dHomeMedicalCentrePatient;
		pairMedicalCentrePatient.getFuture().setStartTime(dropOffTime);		
		pairMedicalCentrePatient.getFuture().setEndTime(dropOffTime); // considering the possibility that patient and paramedic have to wait at medical centre
		// Service time: start time and duration service
		pairMedicalCentrePatient.getFuture().setStartServiceTime(pairMedicalCentrePatient.getFuture().getEndTime()); // start time <- latest time
		pairMedicalCentrePatient.getFuture().setserviceTime(test.getloadTimePatient());
	}



	private Couple creatingCoupleMedicalCentreToPatientHome(Jobs j) {
		//j.getsubJobPair(): Medical centre
		// j - location: Patient home

		Jobs presentJob= new Jobs(j.getsubJobPair().getId(), j.getStartTime(),j.getEndTime(), j.getReqQualification(), j.getReqTime());// medical centre - pick up
		Jobs futureJob= new Jobs(j); // patient home - drop off
		presentJob.setPair(futureJob);
		int directConnectionDistance= input.getCarCost().getCost(presentJob.getId(), futureJob.getId()); // setting the time for picking up the patient and paramedic at medical centre
		Couple pairMedicalCentrePatient=creatingPairMedicalCentrePatient(presentJob,futureJob, directConnectionDistance);
		return pairMedicalCentrePatient;
	}



	private void settingPeopleInSubJob(Couple pairPatientMedicalCentre, int i, int j) {
		pairPatientMedicalCentre.getPresent().setTotalPeople(i); // 1 persona porque se recoge sólo al paciente
		pairPatientMedicalCentre.getFuture().setTotalPeople(j); // setting el numero de personas en el servicio // es un dos porque es el paramedico y el pacnete al mismo tiempo	
	}



	private void computingTimeWindowPatientMedicalCentreSubJob(Couple pairPatientMedicalCentre, Jobs j) {
		// Time window
		// 1. Future Job: drop-off patient and paramedic at medical centre
		// required time before the service starts
		int previousTime=test.getRegistrationTime()+test.getloadTimePatient();
		pairPatientMedicalCentre.getFuture().setStartTime(j.getStartTime()-previousTime); // earliest
		pairPatientMedicalCentre.getFuture().setEndTime(j.getStartTime()-previousTime); // latest
		// Service time: start time and duration service
		pairPatientMedicalCentre.getFuture().setStartServiceTime(j.getStartTime()); // start time
		pairPatientMedicalCentre.getFuture().setserviceTime(j.getReqTime()); // duration service


		// 2. Present Job: pick patient up time is set assuming a direct connection from patient home to medical centre
		// required time before the service starts
		int dPatientHomeMedicalCentre= input.getCarCost().getCost(pairPatientMedicalCentre.getPresent().getId(), pairPatientMedicalCentre.getFuture().getId());	
		previousTime=test.getloadTimePatient()+dPatientHomeMedicalCentre;
		double pickUpTime=Math.max(0,pairPatientMedicalCentre.getFuture().getStartTime()-previousTime); // considera el porque es el que tiene cargado el tiempo de registro
		pairPatientMedicalCentre.getPresent().setStartTime(pickUpTime);
		pairPatientMedicalCentre.getPresent().setEndTime(pickUpTime);
		// Service time: start time and duration service
		pairPatientMedicalCentre.getPresent().setStartServiceTime(pickUpTime);
		pairPatientMedicalCentre.getPresent().setserviceTime(test.getloadTimePatient());

	}



	private Couple creatingCouplePatientHomeToMedicalCentre(Jobs j) {
		// j- location: Patiente home
		// j.getsubJobPair()- location: Medical centre
		Jobs presentJob= new Jobs(j);// Patient home - pick up
		Jobs futureJob= new Jobs(j.getsubJobPair().getId(), j.getStartTime(),j.getEndTime(), j.getReqQualification(), j.getReqTime()); // medical centre - drop-off 
		presentJob.setPair(futureJob);
		int directConnectionDistance= input.getCarCost().getCost(presentJob.getId(), futureJob.getId()); // setting the time for picking up the patient at home patient
		Couple pairPatientMedicalCentre=creatingPairPatientMedicalCentre(presentJob,futureJob, directConnectionDistance);
		return pairPatientMedicalCentre;
	}




	/*
	presentJob<- es el nodo en donde el paciente es recogido para ser llevado al centro médico
	futureJob<- es el centro médico en donde será dejado el paciente
	 */
	private Couple creatingPairMedicalCentrePatient(Jobs presentJob, Jobs futureJob, int directConnectionDistance) {

		// 1. Calculate the time for piking up the patient and paramedic
		// DEFINITION OF FUTURE TASK<- DROP-OFF PATIENT AND PARAMEDIC AT MEDICAL CENTRE
		// the time for the future task is modified to consider the registration time
		// start time= doctor appointment - registration time
		futureJob.setStartTime(futureJob.getStartTime()-test.getRegistrationTime());

		// DEFINITION OF PRESENT TASK<- PICK UP PATIENT AT MEDICAL CENTRE
		// 2. Set the time for pick up patient at the patient home = doctor appointment time  - max(detour, direct connection) - load time - tiempo de registro
		double fixedTime=futureJob.getStartTime()-directConnectionDistance- test.getloadTimePatient(); // doctor appointment
		presentJob.setStartTime(fixedTime);


		// DEFINITION OF A COUPLE
		// 3. creation of the coupe
		Couple presentCouple= new Couple(presentJob,futureJob, directConnectionDistance,test.getDetour());

		return presentCouple;
	}



	private Couple creatingPairPatientMedicalCentre(Jobs presentJob, Jobs futureJob, int directConnectionDistance) {
		// 1. Calculate the time for piking up the patient
		// DEFINITION OF FUTURE TASK<- DROP-OFF PATIENT AND PARAMEDIC AT MEDICAL CENTRE
		// the time for the future task is modified to consider the registration time
		// start time= doctor appointment - registration time
		futureJob.setTimeWindowsDropOffMedicalCentre(test.getRegistrationTime());

		// DEFINITION OF PRESENT TASK<- PICK UP PATIENT AT MEDICAL CENTRE
		// 2. Set the time for pick up patient at the patient home = doctor appointment time  - max(detour, direct connection) - load time - tiempo de registro
		presentJob.setTimeWindowsPickUpMedicalCentre(futureJob.getStartTime(),directConnectionDistance,test.getCumulativeWaitingTime());


		// DEFINITION OF A COUPLE
		// 3. creation of the coupe
		Couple presentCouple= new Couple(presentJob,futureJob, directConnectionDistance, test.getDetour());
		return presentCouple;
	}





	private ArrayList<Couple> createClientJobs() {
		ArrayList<Couple> coupleFromWalkingRoutes= new ArrayList<Couple>();
		HashMap<Integer,Jobs> jobsInWalkingRoutes= clientInWalkingRoutes(); // store the list of job in the walking routes
		// 1. WALKING ROUTES-- Convert walking route in big jobs
		convertingWalkingRoutesInOneTask(coupleFromWalkingRoutes);
		// Individual client JOBS
		for(Jobs j: input.getclients()) {
			// Creating the request for picking up the nurse
			if(!jobsInWalkingRoutes.containsKey(j.getId())) { // only jobs which are not in a walking route	
				Jobs presentJob= new Jobs(j);
				Jobs futureJob= creatingSubPairJOb(j);
				//0. creation of couple
				Couple pickUpDropOff= creatingCoupleforIndividualJobs(presentJob,futureJob); // individula jobs <- not walking routes
				// 1. fixing time windows
				computingTimeatClientHomeSubJob(pickUpDropOff,j); // considering the load un loading time
				// 2. fixing number of people involved
				settingPeopleInSubJob(pickUpDropOff, -1,+1); //- drop-off #personas + pick up #personas
				// 3. checking information
				System.out.println(pickUpDropOff.toString());
				// 4. adding couple
				coupleFromWalkingRoutes.add(pickUpDropOff);
			}
		}
		// create in the class Couple a constructor for setting the walking routes
		return coupleFromWalkingRoutes;
	}



	private Jobs creatingSubPairJOb(Jobs j) {
		double pickUpTimeEarly=j.getstartServiceTime()+j.getReqTime();
		double pickUpTimeLate=j.getstartServiceTime()+j.getReqTime()+test.getCumulativeWaitingTime();
		Jobs futureJob= new Jobs(j.getId(),pickUpTimeEarly,pickUpTimeLate,j.getReqQualification(),test.getloadTimeHomeCareStaff()); 
		j.setPair(futureJob);
		return futureJob;
	}







	private void computingTimeatClientHomeSubJob(Couple DropOffpickUp, Jobs j) {
		// 1. Calculate the time for drop-Off home care staff at client home
		double dropOffTimeEarly=DropOffpickUp.getPresent().getStartTime()-test.getloadTimeHomeCareStaff();
		double dropOffTimeLate=DropOffpickUp.getPresent().getEndTime()-test.getloadTimeHomeCareStaff();
		DropOffpickUp.getPresent().setStartTime(dropOffTimeEarly);
		DropOffpickUp.getPresent().setEndTime(dropOffTimeLate);
		// 2. Calculate the time for pick-Up home care staff at client home
		double pickUpTimeEarly=DropOffpickUp.getFuture().getstartServiceTime()+DropOffpickUp.getFuture().getReqTime();
		double pickUpTimeLate=DropOffpickUp.getFuture().getstartServiceTime()+DropOffpickUp.getFuture().getReqTime()+test.getCumulativeWaitingTime();
		DropOffpickUp.getFuture().setStartTime(pickUpTimeEarly);
		DropOffpickUp.getFuture().setEndTime(pickUpTimeLate);
	}







	private Couple creatingCoupleforIndividualJobs(Jobs presentJob, Jobs futureJob) {
		presentJob.setPair(futureJob);
		Couple presentCouple= new Couple(presentJob,futureJob);
		return presentCouple;
	}







	private void convertingWalkingRoutesInOneTask(ArrayList<Couple> coupleFromWalkingRoutes) {
		for(SubRoute r:subroutes.getWalkingRoutes()) {
			if(r.getDropOffNode()!=null && r.getPickUpNode()!=null) {
				double walkingRouteLength=r.getDurationWalkingRoute();

				// 0. creation of subjobs and fixing time windows 
				Jobs present=creatinngPresentJobFromWR(r.getDropOffNode(),walkingRouteLength);
				Jobs future=creatinngFutureJobFromWR(present, r.getPickUpNode());
				//1. creation of couple
				Couple pairPickUpDropOffHCS=creatingCoupleClientHome(present,future); 

				// 2. fixing number of people involved
				settingPeopleInSubJob(pairPickUpDropOffHCS, -1,+1); //- drop-off #personas + pick up #personas

				// 4. adding couple
				coupleFromWalkingRoutes.add(pairPickUpDropOffHCS);
				// 3. checking information
				System.out.println(pairPickUpDropOffHCS.toString());

			}
		}
	}







	private Couple creatingCoupleClientHome(Jobs presentJob, Jobs futureJob) {
		presentJob.setPair(futureJob);
		int directConnectionDistance= input.getCarCost().getCost(presentJob.getId(), futureJob.getId()); // setting the time for picking up the patient at home patient
		Couple pairPatientMedicalCentre=creatingPairPickUpDeliveryHCS(presentJob,futureJob, directConnectionDistance);
		return pairPatientMedicalCentre;
	}







	private Couple creatingPairPickUpDeliveryHCS(Jobs presentJob, Jobs futureJob, int directConnectionDistance) {
		Couple presentCouple= new Couple(presentJob,futureJob, directConnectionDistance, test.getDetour());
		return presentCouple;
	}







	private HashMap<Integer, Jobs> clientInWalkingRoutes() {
		HashMap<Integer,Jobs> jobsInWalkingRoutes= new HashMap<Integer,Jobs>();
		for(SubRoute r:subroutes.getWalkingRoutes()) {
			if(r.getJobSequence().size()>1) {
				for(Jobs j:r.getJobSequence()) {
					jobsInWalkingRoutes.put(j.getId(), j); // storing the job in the walking route
				}
			}
		}
		return jobsInWalkingRoutes;
	}







	private Jobs creatinngFutureJobFromWR(Jobs present,Jobs pickUpNode) {
		double startTime= pickUpNode.getstartServiceTime()+pickUpNode.getReqTime(); // early time window = start time service + time requested // lastest=  start time service + time requested + max waiting time
		double endTime = startTime+ test.getCumulativeWaitingTime();
		Jobs future= new Jobs(pickUpNode.getId(),startTime,endTime ,pickUpNode.getReqQualification(), test.getloadTimeHomeCareStaff()); // Jobs(int id, int startTime, int endTime, int reqQualification,int reqTime)
		return future;
	}



	private Jobs creatinngPresentJobFromWR(Jobs dropOffNode, double walkingRouteLength) {
		// start when the service
		double previousTime= dropOffNode.getstartServiceTime()-test.getloadTimeHomeCareStaff(); // early time window = start time service + time requested // lastest=  start time service + time requested + max waiting time
		Jobs present= new Jobs(dropOffNode.getId(),previousTime,previousTime ,dropOffNode.getReqQualification(), walkingRouteLength); // Jobs(int id, int startTime, int endTime, int reqQualification,int reqTime)
		return present;
	}



	private Jobs creatingTheFeatureJob(Jobs j) {
		int startTime= (int)j.getstartServiceTime()+(int)j.getReqTime(); // early time window = start time service + time requested // lastest=  start time service + time requested + max waiting time
		int endTime= (int)j.getstartServiceTime()+(int)j.getReqTime()+test.getCumulativeWaitingTime(); // early time window = start time service + time requested // lastest=  start time service + time requested + max waiting time
		Jobs future= new Jobs(j.getId(),startTime,endTime ,j.getReqQualification(), 0); // Jobs(int id, int startTime, int endTime, int reqQualification,int reqTime)
		return future;
	}



	// Getters
	public WalkingRoutes getSubroutes() {
		return subroutes;
	}



	public DrivingRoutes getRoutes() {
		return routes;
	}




}
