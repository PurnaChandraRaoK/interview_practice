/*
Meeting Room Scheduler (LLD)
- Add meeting rooms with capacity
- Find available rooms for timeslot + participant count
- Book using room selection strategy (FCFS here)
- Notify participants
- Maintain meeting history (repository)
- Check participant availability via their calendars
- Cancel meeting
- Update meeting (timeslot/participants) - simple version
*/

public class MeetingRoomSchedulerApp {

    // -------------------- Notification (Observer-ish) --------------------

    interface Observer {
        void update(String message);
    }

    static class Participant implements Observer {
        private final String name;
        private final String email;
        private final String mobile;
        private final BookingCalendar calendar;

        public Participant(String name, String email, String mobile) {
            this.name = name;
            this.email = email;
            this.mobile = mobile;
            this.calendar = new BookingCalendar();
        }

        public String getEmail() { return email; }
        public String getName() { return name; }
        public BookingCalendar getCalendar() { return calendar; }

        @Override
        public void update(String message) {
            System.out.println("Email sent to " + this.email + ": " + message);
        }

        @Override
        public String toString() {
            return "Participant{" + name + ", " + email + '}';
        }
    }

    static class NotificationService {
        public void notifyParticipants(List<Participant> participants, String message) {
            for (Participant p : participants) {
                p.update(message);
            }
        }
    }

    // -------------------- Core Domain --------------------

    static class Location {
        private final int floorId;
        private final int buildingId;

        public Location(int floorId, int buildingId) {
            this.floorId = floorId;
            this.buildingId = buildingId;
        }

        public int getFloorId() { return floorId; }
        public int getBuildingId() { return buildingId; }

        @Override
        public String toString() {
            return "B" + buildingId + "-F" + floorId;
        }
    }

    static class TimeSlot {
        private final int startTime;
        private final int endTime;

        public TimeSlot(int startTime, int endTime) {
            if (startTime >= endTime) throw new IllegalArgumentException("startTime must be < endTime");
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public int getStartTime() { return startTime; }
        public int getEndTime() { return endTime; }

        // Correct overlap: [a,b) overlaps [c,d) if a < d && c < b
        public boolean overlaps(TimeSlot other) {
            return this.startTime < other.endTime && other.startTime < this.endTime;
        }

        @Override
        public String toString() {
            return startTime + "-" + endTime;
        }
    }

    static class BookingCalendar {
        private final List<TimeSlot> bookings = new ArrayList<>();

        public boolean isAvailable(TimeSlot slot) {
            for (TimeSlot b : bookings) {
                if (b.overlaps(slot)) return false;
            }
            return true;
        }

        public void addTimeSlot(TimeSlot slot) {
            bookings.add(slot);
        }

        public void removeTimeSlot(TimeSlot slot) {
            bookings.removeIf(b -> b.getStartTime() == slot.getStartTime() && b.getEndTime() == slot.getEndTime());
        }

        public List<TimeSlot> getBookings() {
            return Collections.unmodifiableList(bookings);
        }
    }

    static class MeetingRoom {
        private final String roomId;
        private final int capacity;
        private final Location location;
        private final BookingCalendar calendar;

        public MeetingRoom(String roomName, Location location, int capacity) {
            this.roomId = roomName + "_" + location.getFloorId() + "_" + location.getBuildingId();
            this.capacity = capacity;
            this.location = location;
            this.calendar = new BookingCalendar();
        }

        public String getRoomId() { return roomId; }
        public int getCapacity() { return capacity; }
        public Location getLocation() { return location; }
        public BookingCalendar getCalendar() { return calendar; }

        public boolean isAvailable(TimeSlot slot) {
            return calendar.isAvailable(slot);
        }

        @Override
        public String toString() {
            return "Room{" + roomId + ", cap=" + capacity + ", loc=" + location + "}";
        }
    }

    static class Meeting {
        private final String meetingId;
        private String title;
        private List<Participant> participants;
        private TimeSlot timeSlot;
        private final MeetingRoom room;

        public Meeting(String meetingId, String title, List<Participant> participants, TimeSlot timeSlot, MeetingRoom room) {
            this.meetingId = meetingId;
            this.title = title;
            this.participants = new ArrayList<>(participants);
            this.timeSlot = timeSlot;
            this.room = room;
        }

        public String getMeetingId() { return meetingId; }
        public String getTitle() { return title; }
        public List<Participant> getParticipants() { return Collections.unmodifiableList(participants); }
        public TimeSlot getTimeSlot() { return timeSlot; }
        public MeetingRoom getRoom() { return room; }

        public void setTitle(String title) { this.title = title; }
        public void setParticipants(List<Participant> participants) { this.participants = new ArrayList<>(participants); }
        public void setTimeSlot(TimeSlot timeSlot) { this.timeSlot = timeSlot; }

        @Override
        public String toString() {
            return "Meeting{" + meetingId + ", '" + title + "', slot=" + timeSlot + ", room=" + room.getRoomId() + "}";
        }
    }

    // -------------------- Strategy --------------------

    interface RoomSelectionStrategy {
        MeetingRoom selectRoom(List<MeetingRoom> availableRooms);
    }

    static class FirstComeFirstServeStrategy implements RoomSelectionStrategy {
        @Override
        public MeetingRoom selectRoom(List<MeetingRoom> availableRooms) {
            if (availableRooms == null || availableRooms.isEmpty()) return null;
            return availableRooms.get(0);
        }
    }

    // -------------------- Repository --------------------

    interface MeetingRepository {
        void save(Meeting meeting);
        Meeting findById(String meetingId);
        List<Meeting> findAll();
        void delete(String meetingId);
    }

    static class InMemoryMeetingRepository implements MeetingRepository {
        private final Map<String, Meeting> store = new ConcurrentHashMap<>();

        @Override
        public void save(Meeting meeting) {
            store.put(meeting.getMeetingId(), meeting);
        }

        @Override
        public Meeting findById(String meetingId) {
            return store.get(meetingId);
        }

        @Override
        public List<Meeting> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public void delete(String meetingId) {
            store.remove(meetingId);
        }
    }

    // -------------------- Services --------------------

    static class MeetingRoomController {
        private final List<MeetingRoom> meetingRooms = new ArrayList<>();

        public void addMeetingRoom(MeetingRoom room) {
            meetingRooms.add(room);
        }

        public List<MeetingRoom> getAvailableMeetingRooms(TimeSlot slot, int numParticipants) {
            List<MeetingRoom> availableRooms = new ArrayList<>();
            for (MeetingRoom room : meetingRooms) {
                if (room.getCapacity() >= numParticipants && room.isAvailable(slot)) {
                    availableRooms.add(room);
                }
            }
            return availableRooms;
        }

        public List<MeetingRoom> getAllRooms() {
            return Collections.unmodifiableList(meetingRooms);
        }
    }

    static class ParticipantAvailabilityService {
        public boolean areAllAvailable(List<Participant> participants, TimeSlot slot) {
            for (Participant p : participants) {
                if (!p.getCalendar().isAvailable(slot)) return false;
            }
            return true;
        }

        public void blockCalendars(List<Participant> participants, TimeSlot slot) {
            for (Participant p : participants) {
                p.getCalendar().addTimeSlot(slot);
            }
        }

        public void freeCalendars(List<Participant> participants, TimeSlot slot) {
            for (Participant p : participants) {
                p.getCalendar().removeTimeSlot(slot);
            }
        }
    }

    static class MeetingScheduler {
        private final RoomSelectionStrategy selectionStrategy;
        private final MeetingRoomController roomController;
        private final MeetingRepository meetingRepository;
        private final ParticipantAvailabilityService participantAvailabilityService;
        private final NotificationService notificationService;

        public MeetingScheduler(RoomSelectionStrategy selectionStrategy,
                                MeetingRoomController roomController,
                                MeetingRepository meetingRepository,
                                ParticipantAvailabilityService participantAvailabilityService,
                                NotificationService notificationService) {
            this.selectionStrategy = selectionStrategy;
            this.roomController = roomController;
            this.meetingRepository = meetingRepository;
            this.participantAvailabilityService = participantAvailabilityService;
            this.notificationService = notificationService;
        }

        public Meeting scheduleMeeting(String meetingId, String title, List<Participant> participants, TimeSlot timeSlot) {
            // 1) Check participant availability
            if (!participantAvailabilityService.areAllAvailable(participants, timeSlot)) {
                System.out.println("Some participants are not available for slot " + timeSlot);
                return null;
            }

            // 2) Find available rooms
            List<MeetingRoom> availableRooms = roomController.getAvailableMeetingRooms(timeSlot, participants.size());
            MeetingRoom selectedRoom = selectionStrategy.selectRoom(availableRooms);

            if (selectedRoom == null) {
                System.out.println("No room available for slot " + timeSlot + " and participants=" + participants.size());
                return null;
            }

            // 3) Book room + block participant calendars
            selectedRoom.getCalendar().addTimeSlot(timeSlot);
            participantAvailabilityService.blockCalendars(participants, timeSlot);

            // 4) Create meeting + persist
            Meeting meeting = new Meeting(meetingId, title, participants, timeSlot, selectedRoom);
            meetingRepository.save(meeting);

            // 5) Notify
            notificationService.notifyParticipants(participants,
                    "Meeting '" + title + "' scheduled in room " + selectedRoom.getRoomId() + " for slot " + timeSlot);

            System.out.println("Scheduled: " + meeting);
            return meeting;
        }

        public boolean cancelMeeting(String meetingId) {
            Meeting meeting = meetingRepository.findById(meetingId);
            if (meeting == null) return false;

            // Free room + participant calendars
            meeting.getRoom().getCalendar().removeTimeSlot(meeting.getTimeSlot());
            participantAvailabilityService.freeCalendars(meeting.getParticipants(), meeting.getTimeSlot());

            meetingRepository.delete(meetingId);

            notificationService.notifyParticipants(meeting.getParticipants(),
                    "Meeting '" + meeting.getTitle() + "' canceled (id=" + meetingId + ")");
            System.out.println("Canceled meeting: " + meetingId);
            return true;
        }

        // Simple update: change slot and/or participants (same room selection flow)
        public Meeting updateMeeting(String meetingId, String newTitle, List<Participant> newParticipants, TimeSlot newSlot) {
            Meeting existing = meetingRepository.findById(meetingId);
            if (existing == null) return null;

            // 1) Free old bookings first
            existing.getRoom().getCalendar().removeTimeSlot(existing.getTimeSlot());
            participantAvailabilityService.freeCalendars(existing.getParticipants(), existing.getTimeSlot());

            // 2) Try reschedule with new data (could pick a different room)
            String titleToUse = (newTitle == null) ? existing.getTitle() : newTitle;
            List<Participant> participantsToUse = (newParticipants == null) ? existing.getParticipants() : newParticipants;
            TimeSlot slotToUse = (newSlot == null) ? existing.getTimeSlot() : newSlot;

            Meeting newMeeting = scheduleMeeting(meetingId, titleToUse, participantsToUse, slotToUse);
            if (newMeeting == null) {
                // rollback: re-book old meeting if update fails
                scheduleMeeting(existing.getMeetingId(), existing.getTitle(), existing.getParticipants(), existing.getTimeSlot());
                return null;
            }
            return newMeeting;
        }

        public List<Meeting> history() {
            return meetingRepository.findAll();
        }
    }

    // -------------------- Demo --------------------

    public static void main(String[] args) {
        MeetingRoom room1 = new MeetingRoom("Room1", new Location(1, 101), 3);
        MeetingRoom room2 = new MeetingRoom("Room2", new Location(1, 101), 2);
        MeetingRoom room3 = new MeetingRoom("Room3", new Location(1, 101), 5);

        MeetingRoomController controller = new MeetingRoomController();
        controller.addMeetingRoom(room1);
        controller.addMeetingRoom(room2);
        controller.addMeetingRoom(room3);

        Participant p1 = new Participant("Shreya", "ab@gmail.com", "+91234567890");
        Participant p2 = new Participant("Shravani", "xyz@gmail.com", "+91234567890");

        List<Participant> participants = new ArrayList<>();
        participants.add(p1);
        participants.add(p2);

        MeetingScheduler scheduler = new MeetingScheduler(
                new FirstComeFirstServeStrategy(),
                controller,
                new InMemoryMeetingRepository(),
                new ParticipantAvailabilityService(),
                new NotificationService()
        );

        scheduler.scheduleMeeting("M1", "Archiving Discussion", participants, new TimeSlot(2, 3));
        scheduler.scheduleMeeting("M2", "Workflow Discussion", participants, new TimeSlot(2, 3)); // should fail (participants busy)

        scheduler.cancelMeeting("M1");
        scheduler.scheduleMeeting("M3", "Workflow Discussion", participants, new TimeSlot(2, 3)); // should succeed now

        // Update example
        scheduler.updateMeeting("M3", "Workflow Discussion - Updated", participants, new TimeSlot(3, 4));

        System.out.println("History: " + scheduler.history());
    }
}
