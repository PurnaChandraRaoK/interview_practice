import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/*
Hospital Appointment Scheduler (LLD)
- Register patients, add doctors (dept/specialization)
- Find available doctors for a slot
- Book appointment (prevent double booking of doctor slot)
- Optional: book by department using selection strategy (FCFS)
- Notify patient + doctor
- Maintain appointment history (repository)
- Check patient availability via their calendar (like participants)
- Cancel appointment
- Update appointment (slot/doctor) - simple version with rollback
- Check-in patient (generate token/queue number per doctor)
*/

public class HospitalAppointmentSchedulerApp {

    // -------------------- Notification (Observer-ish) --------------------

    interface Observer {
        void update(String message);
    }

    static class Patient implements Observer {
        private final String patientId;
        private final String name;
        private final String email;
        private final String mobile;
        private final BookingCalendar calendar;

        public Patient(String patientId, String name, String email, String mobile) {
            this.patientId = Objects.requireNonNull(patientId);
            this.name = Objects.requireNonNull(name);
            this.email = Objects.requireNonNull(email);
            this.mobile = Objects.requireNonNull(mobile);
            this.calendar = new BookingCalendar();
        }

        public String getPatientId() { return patientId; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public BookingCalendar getCalendar() { return calendar; }

        @Override
        public void update(String message) {
            System.out.println("Email sent to " + email + ": " + message);
        }

        @Override
        public String toString() {
            return "Patient{" + patientId + ", " + name + "}";
        }
    }

    static class Doctor implements Observer {
        private final String doctorId;
        private final String name;
        private final Department department;
        private final String specialization;
        private final BookingCalendar calendar;

        public Doctor(String doctorId, String name, Department department, String specialization) {
            this.doctorId = Objects.requireNonNull(doctorId);
            this.name = Objects.requireNonNull(name);
            this.department = Objects.requireNonNull(department);
            this.specialization = Objects.requireNonNull(specialization);
            this.calendar = new BookingCalendar();
        }

        public String getDoctorId() { return doctorId; }
        public String getName() { return name; }
        public Department getDepartment() { return department; }
        public String getSpecialization() { return specialization; }
        public BookingCalendar getCalendar() { return calendar; }

        public boolean isAvailable(TimeSlot slot) {
            return calendar.isAvailable(slot);
        }

        @Override
        public void update(String message) {
            System.out.println("Doctor notification [" + doctorId + " - " + name + "]: " + message);
        }

        @Override
        public String toString() {
            return "Doctor{" + doctorId + ", " + name + ", " + department + ", " + specialization + "}";
        }
    }

    static class NotificationService {
        public void notifyAll(List<Observer> observers, String message) {
            for (Observer o : observers) o.update(message);
        }

        public void notifyAppointmentBooked(Patient p, Doctor d, Appointment appt) {
            notifyAll(List.of(p, d),
                    "Appointment booked (id=" + appt.getAppointmentId() + ") with Dr. " + d.getName()
                            + " for slot " + appt.getTimeSlot());
        }

        public void notifyAppointmentCanceled(Patient p, Doctor d, String appointmentId) {
            notifyAll(List.of(p, d),
                    "Appointment canceled (id=" + appointmentId + ")");
        }

        public void notifyAppointmentUpdated(Patient p, Doctor d, Appointment appt) {
            notifyAll(List.of(p, d),
                    "Appointment updated (id=" + appt.getAppointmentId() + ") new slot " + appt.getTimeSlot()
                            + ", doctor=" + d.getName());
        }

        public void notifyCheckedIn(Patient p, Doctor d, Appointment appt) {
            notifyAll(List.of(p, d),
                    "Patient check-in completed for appointment (id=" + appt.getAppointmentId()
                            + "), token=" + appt.getTokenNumber());
        }
    }

    // -------------------- Core Domain --------------------

    enum AppointmentStatus { BOOKED, CANCELED, CHECKED_IN }

    static class Department {
        private final String deptId;
        private final String name;

        public Department(String deptId, String name) {
            this.deptId = Objects.requireNonNull(deptId);
            this.name = Objects.requireNonNull(name);
        }

        public String getDeptId() { return deptId; }
        public String getName() { return name; }

        @Override
        public String toString() { return name; }
    }

    static class TimeSlot {
        private final int startTime; // keep same style as your sample (e.g., 10 = 10AM)
        private final int endTime;

        public TimeSlot(int startTime, int endTime) {
            if (startTime >= endTime) throw new IllegalArgumentException("startTime must be < endTime");
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public int getStartTime() { return startTime; }
        public int getEndTime() { return endTime; }

        // [a,b) overlaps [c,d) if a < d && c < b
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

    static class Appointment {
        private final String appointmentId;
        private final Patient patient;
        private Doctor doctor;
        private TimeSlot timeSlot;
        private AppointmentStatus status;
        private Integer tokenNumber; // null until check-in

        public Appointment(String appointmentId, Patient patient, Doctor doctor, TimeSlot timeSlot) {
            this.appointmentId = Objects.requireNonNull(appointmentId);
            this.patient = Objects.requireNonNull(patient);
            this.doctor = Objects.requireNonNull(doctor);
            this.timeSlot = Objects.requireNonNull(timeSlot);
            this.status = AppointmentStatus.BOOKED;
        }

        public String getAppointmentId() { return appointmentId; }
        public Patient getPatient() { return patient; }
        public Doctor getDoctor() { return doctor; }
        public TimeSlot getTimeSlot() { return timeSlot; }
        public AppointmentStatus getStatus() { return status; }
        public Integer getTokenNumber() { return tokenNumber; }

        public void setDoctor(Doctor doctor) { this.doctor = doctor; }
        public void setTimeSlot(TimeSlot timeSlot) { this.timeSlot = timeSlot; }
        public void setStatus(AppointmentStatus status) { this.status = status; }
        public void setTokenNumber(Integer tokenNumber) { this.tokenNumber = tokenNumber; }

        @Override
        public String toString() {
            return "Appointment{" + appointmentId +
                    ", patient=" + patient.getName() +
                    ", doctor=" + doctor.getName() +
                    ", slot=" + timeSlot +
                    ", status=" + status +
                    ", token=" + tokenNumber +
                    '}';
        }
    }

    // -------------------- Strategy --------------------

    interface DoctorSelectionStrategy {
        Doctor selectDoctor(List<Doctor> availableDoctors);
    }

    static class FirstComeFirstServeDoctorStrategy implements DoctorSelectionStrategy {
        @Override
        public Doctor selectDoctor(List<Doctor> availableDoctors) {
            if (availableDoctors == null || availableDoctors.isEmpty()) return null;
            return availableDoctors.get(0);
        }
    }

    // -------------------- Repository --------------------

    interface AppointmentRepository {
        void save(Appointment appt);
        Appointment findById(String appointmentId);
        List<Appointment> findAll();
        void delete(String appointmentId);
    }

    static class InMemoryAppointmentRepository implements AppointmentRepository {
        private final Map<String, Appointment> store = new ConcurrentHashMap<>();

        @Override
        public void save(Appointment appt) {
            store.put(appt.getAppointmentId(), appt);
        }

        @Override
        public Appointment findById(String appointmentId) {
            return store.get(appointmentId);
        }

        @Override
        public List<Appointment> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public void delete(String appointmentId) {
            store.remove(appointmentId);
        }
    }

    // -------------------- Services --------------------

    static class PatientAvailabilityService {
        public boolean isPatientAvailable(Patient patient, TimeSlot slot) {
            return patient.getCalendar().isAvailable(slot);
        }

        public void blockPatient(Patient patient, TimeSlot slot) {
            patient.getCalendar().addTimeSlot(slot);
        }

        public void freePatient(Patient patient, TimeSlot slot) {
            patient.getCalendar().removeTimeSlot(slot);
        }
    }

    static class DoctorDirectory {
        private final List<Doctor> doctors = new ArrayList<>();

        public void addDoctor(Doctor d) {
            doctors.add(d);
        }

        public List<Doctor> getAllDoctors() {
            return Collections.unmodifiableList(doctors);
        }

        public List<Doctor> findAvailableDoctors(Department dept, String specialization, TimeSlot slot) {
            List<Doctor> res = new ArrayList<>();
            for (Doctor d : doctors) {
                boolean deptOk = (dept == null) || d.getDepartment().getDeptId().equals(dept.getDeptId());
                boolean specOk = (specialization == null) || d.getSpecialization().equalsIgnoreCase(specialization);
                if (deptOk && specOk && d.isAvailable(slot)) {
                    res.add(d);
                }
            }
            return res;
        }
    }

    static class TokenService {
        // token per doctor per "day" would be typical; here we keep it per-doctor simple.
        private final Map<String, AtomicInteger> doctorCounters = new ConcurrentHashMap<>();

        public int nextToken(String doctorId) {
            doctorCounters.putIfAbsent(doctorId, new AtomicInteger(0));
            return doctorCounters.get(doctorId).incrementAndGet();
        }
    }

    static class LockService {
        // Per-entity locks to avoid races on booking
        private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

        public ReentrantLock lockFor(String key) {
            locks.putIfAbsent(key, new ReentrantLock());
            return locks.get(key);
        }

        // Acquire locks in sorted order to avoid deadlocks
        public void lockBoth(String a, String b) {
            String first = a.compareTo(b) <= 0 ? a : b;
            String second = a.compareTo(b) <= 0 ? b : a;
            lockFor(first).lock();
            lockFor(second).lock();
        }

        public void unlockBoth(String a, String b) {
            String first = a.compareTo(b) <= 0 ? a : b;
            String second = a.compareTo(b) <= 0 ? b : a;
            lockFor(second).unlock();
            lockFor(first).unlock();
        }
    }

    static class AppointmentScheduler {
        private final DoctorSelectionStrategy doctorSelectionStrategy;
        private final DoctorDirectory doctorDirectory;
        private final AppointmentRepository appointmentRepository;
        private final PatientAvailabilityService patientAvailabilityService;
        private final NotificationService notificationService;
        private final TokenService tokenService;
        private final LockService lockService;

        public AppointmentScheduler(DoctorSelectionStrategy doctorSelectionStrategy,
                                    DoctorDirectory doctorDirectory,
                                    AppointmentRepository appointmentRepository,
                                    PatientAvailabilityService patientAvailabilityService,
                                    NotificationService notificationService,
                                    TokenService tokenService,
                                    LockService lockService) {
            this.doctorSelectionStrategy = doctorSelectionStrategy;
            this.doctorDirectory = doctorDirectory;
            this.appointmentRepository = appointmentRepository;
            this.patientAvailabilityService = patientAvailabilityService;
            this.notificationService = notificationService;
            this.tokenService = tokenService;
            this.lockService = lockService;
        }

        // Book with a specific doctor
        public Appointment bookAppointmentWithDoctor(String appointmentId, Patient patient, Doctor doctor, TimeSlot slot) {
            // lock patient + doctor to avoid race/double-booking
            String patientKey = "P:" + patient.getPatientId();
            String doctorKey = "D:" + doctor.getDoctorId();
            lockService.lockBoth(patientKey, doctorKey);
            try {
                if (!patientAvailabilityService.isPatientAvailable(patient, slot)) {
                    System.out.println("Patient not available for slot " + slot);
                    return null;
                }
                if (!doctor.isAvailable(slot)) {
                    System.out.println("Doctor not available for slot " + slot);
                    return null;
                }

                // Book: block doctor + patient
                doctor.getCalendar().addTimeSlot(slot);
                patientAvailabilityService.blockPatient(patient, slot);

                Appointment appt = new Appointment(appointmentId, patient, doctor, slot);
                appointmentRepository.save(appt);

                notificationService.notifyAppointmentBooked(patient, doctor, appt);
                System.out.println("Booked: " + appt);
                return appt;
            } finally {
                lockService.unlockBoth(patientKey, doctorKey);
            }
        }

        // Optional: Book by department/specialization (strategy selects doctor)
        public Appointment bookAppointmentByDept(String appointmentId, Patient patient,
                                                 Department dept, String specialization, TimeSlot slot) {
            List<Doctor> available = doctorDirectory.findAvailableDoctors(dept, specialization, slot);
            Doctor selected = doctorSelectionStrategy.selectDoctor(available);
            if (selected == null) {
                System.out.println("No doctor available for dept=" + (dept == null ? "ANY" : dept)
                        + ", spec=" + (specialization == null ? "ANY" : specialization)
                        + ", slot=" + slot);
                return null;
            }
            return bookAppointmentWithDoctor(appointmentId, patient, selected, slot);
        }

        public boolean cancelAppointment(String appointmentId) {
            Appointment appt = appointmentRepository.findById(appointmentId);
            if (appt == null) return false;
            if (appt.getStatus() == AppointmentStatus.CANCELED) return true;

            Patient patient = appt.getPatient();
            Doctor doctor = appt.getDoctor();
            TimeSlot slot = appt.getTimeSlot();

            String patientKey = "P:" + patient.getPatientId();
            String doctorKey = "D:" + doctor.getDoctorId();
            lockService.lockBoth(patientKey, doctorKey);
            try {
                // free calendars
                doctor.getCalendar().removeTimeSlot(slot);
                patientAvailabilityService.freePatient(patient, slot);

                appt.setStatus(AppointmentStatus.CANCELED);
                appointmentRepository.delete(appointmentId);

                notificationService.notifyAppointmentCanceled(patient, doctor, appointmentId);
                System.out.println("Canceled appointment: " + appointmentId);
                return true;
            } finally {
                lockService.unlockBoth(patientKey, doctorKey);
            }
        }

        // Simple update: change slot and/or doctor (rebook flow). Rollback if fails.
        public Appointment updateAppointment(String appointmentId, Doctor newDoctor, TimeSlot newSlot) {
            Appointment existing = appointmentRepository.findById(appointmentId);
            if (existing == null) return null;
            if (existing.getStatus() == AppointmentStatus.CANCELED) return null;

            Patient patient = existing.getPatient();
            Doctor oldDoctor = existing.getDoctor();
            TimeSlot oldSlot = existing.getTimeSlot();

            // Step1: free old booking
            String oldPatientKey = "P:" + patient.getPatientId();
            String oldDoctorKey = "D:" + oldDoctor.getDoctorId();
            lockService.lockBoth(oldPatientKey, oldDoctorKey);
            try {
                oldDoctor.getCalendar().removeTimeSlot(oldSlot);
                patientAvailabilityService.freePatient(patient, oldSlot);
            } finally {
                lockService.unlockBoth(oldPatientKey, oldDoctorKey);
            }

            // Step2: try new booking (could be same doctor/slot)
            Doctor doctorToUse = (newDoctor == null) ? oldDoctor : newDoctor;
            TimeSlot slotToUse = (newSlot == null) ? oldSlot : newSlot;

            Appointment updated = bookAppointmentWithDoctor(appointmentId, patient, doctorToUse, slotToUse);
            if (updated == null) {
                // rollback: re-book old appointment
                Appointment rollback = bookAppointmentWithDoctor(appointmentId, patient, oldDoctor, oldSlot);
                if (rollback != null) {
                    System.out.println("Update failed; rolled back to old appointment booking.");
                }
                return null;
            }

            // preserve status/token semantics if you want; for simplicity: BOOKED and token reset
            updated.setStatus(AppointmentStatus.BOOKED);
            updated.setTokenNumber(null);

            notificationService.notifyAppointmentUpdated(patient, doctorToUse, updated);
            return updated;
        }

        // Check-in: generate token for doctor queue; keep appointment booked but mark CHECKED_IN
        public Appointment checkIn(String appointmentId) {
            Appointment appt = appointmentRepository.findById(appointmentId);
            if (appt == null) return null;
            if (appt.getStatus() == AppointmentStatus.CANCELED) return null;

            Patient p = appt.getPatient();
            Doctor d = appt.getDoctor();

            String patientKey = "P:" + p.getPatientId();
            String doctorKey = "D:" + d.getDoctorId();
            lockService.lockBoth(patientKey, doctorKey);
            try {
                if (appt.getStatus() == AppointmentStatus.CHECKED_IN) return appt;

                int token = tokenService.nextToken(d.getDoctorId());
                appt.setTokenNumber(token);
                appt.setStatus(AppointmentStatus.CHECKED_IN);
                appointmentRepository.save(appt);

                notificationService.notifyCheckedIn(p, d, appt);
                System.out.println("Checked-in: " + appt);
                return appt;
            } finally {
                lockService.unlockBoth(patientKey, doctorKey);
            }
        }

        public List<Appointment> history() {
            return appointmentRepository.findAll();
        }
    }

    // -------------------- Demo --------------------

    public static void main(String[] args) {
        Department cardio = new Department("D1", "Cardiology");
        Department ent = new Department("D2", "ENT");

        Doctor d1 = new Doctor("DOC1", "Dr. Rao", cardio, "Cardiologist");
        Doctor d2 = new Doctor("DOC2", "Dr. Meera", cardio, "Cardiologist");
        Doctor d3 = new Doctor("DOC3", "Dr. Vikram", ent, "ENT");

        DoctorDirectory directory = new DoctorDirectory();
        directory.addDoctor(d1);
        directory.addDoctor(d2);
        directory.addDoctor(d3);

        Patient p1 = new Patient("P1", "Shreya", "shreya@gmail.com", "+91xxxx");
        Patient p2 = new Patient("P2", "Shravani", "shravani@gmail.com", "+91xxxx");

        AppointmentScheduler scheduler = new AppointmentScheduler(
                new FirstComeFirstServeDoctorStrategy(),
                directory,
                new InMemoryAppointmentRepository(),
                new PatientAvailabilityService(),
                new NotificationService(),
                new TokenService(),
                new LockService()
        );

        TimeSlot slot10to11 = new TimeSlot(10, 11);
        TimeSlot slot11to12 = new TimeSlot(11, 12);

        // 1) Book with a specific doctor
        scheduler.bookAppointmentWithDoctor("A1", p1, d1, slot10to11);

        // 2) Try double-booking same doctor slot -> should fail
        scheduler.bookAppointmentWithDoctor("A2", p2, d1, slot10to11);

        // 3) Book by department (FCFS picks first available doctor)
        scheduler.bookAppointmentByDept("A3", p2, cardio, "Cardiologist", slot10to11); // should pick d2

        // 4) Check-in generates token
        scheduler.checkIn("A1");

        // 5) Update appointment time
        scheduler.updateAppointment("A3", null, slot11to12);

        // 6) Cancel
        scheduler.cancelAppointment("A1");

        System.out.println("History: " + scheduler.history());
    }
}
