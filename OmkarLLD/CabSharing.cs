using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;

namespace CabSharingApp
{
    // Simple 2D point representing locations
    public record Point(double X, double Y)
    {
        public double DistanceTo(Point other)
        {
            var dx = X - other.X;
            var dy = Y - other.Y;
            return Math.Sqrt(dx * dx + dy * dy);
        }

        public override string ToString() => $"({X:0.##}, {Y:0.##})";
    }

    // Base user
    public abstract class User
    {
        public Guid Id { get; }
        public string Name { get; }

        protected User(string name)
        {
            Id = Guid.NewGuid();
            Name = name;
        }

        public override string ToString() => $"{Name} [{Id}]";
    }

    public class Rider : User
    {
        internal List<Ride> rideHistory = new();
        public IReadOnlyList<Ride> RideHistory => rideHistory.AsReadOnly();

        public Rider(string name) : base(name) { }
    }

    public class Driver : User
    {
        private readonly object availabilityLock = new();
        public Point Location { get; private set; }
        public bool IsAvailable { get; private set; } = true;
        internal List<Ride> rideHistory = new();
        public IReadOnlyList<Ride> RideHistory => rideHistory.AsReadOnly();

        public Driver(string name, Point initialLocation) : base(name)
        {
            Location = initialLocation;
        }

        public void UpdateLocation(Point p)
        {
            Location = p;
        }

        internal bool TrySetUnavailable()
        {
            lock (availabilityLock)
            {
                if (!IsAvailable) return false;
                IsAvailable = false;
                return true;
            }
        }

        internal void SetAvailable()
        {
            lock (availabilityLock)
            {
                IsAvailable = true;
            }
        }
    }

    public enum RideStatus
    {
        Requested,
        Assigned,
        Started,
        Completed,
        Cancelled
    }

    public class Ride
    {
        public Guid Id { get; }
        public Rider Rider { get; }
        public Driver? Driver { get; internal set; }
        public Point Pickup { get; }
        public Point Destination { get; }
        public RideStatus Status { get; internal set; }
        public DateTime RequestedAt { get; }
        public DateTime? StartedAt { get; internal set; }
        public DateTime? CompletedAt { get; internal set; }
        public double Fare { get; internal set; }

        public Ride(Rider rider, Point pickup, Point destination)
        {
            Id = Guid.NewGuid();
            Rider = rider;
            Pickup = pickup;
            Destination = destination;
            Status = RideStatus.Requested;
            RequestedAt = DateTime.UtcNow;
        }

        public double Distance => Pickup.DistanceTo(Destination);

        public override string ToString()
        {
            return $"Ride {Id} - Rider: {Rider.Name}, Driver: {(Driver?.Name ?? "(unassigned)")}, From: {Pickup} To: {Destination}, Status: {Status}";
        }
    }

    // Service for managing drivers
    public class DriverService
    {
        private readonly Dictionary<Guid, Driver> drivers = new();

        public Driver RegisterDriver(string name, Point initialLocation)
        {
            var d = new Driver(name, initialLocation);
            drivers[d.Id] = d;
            return d;
        }

        public bool UpdateDriverLocation(Guid driverId, Point newLocation)
        {
            if (!drivers.TryGetValue(driverId, out var d)) return false;
            d.UpdateLocation(newLocation);
            return true;
        }

        public Driver? GetDriver(Guid id) => drivers.TryGetValue(id, out var d) ? d : null;

        public IEnumerable<Driver> ListDrivers() => drivers.Values;

        public IReadOnlyList<Ride> GetDriverHistory(Guid driverId)
        {
            if (!drivers.TryGetValue(driverId, out var d)) return Array.Empty<Ride>();
            return d.RideHistory;
        }

        public List<Driver> GetAvailableDrivers() => drivers.Values.Where(d => d.IsAvailable).ToList();
    }

    // Service for managing rides and matching
    public class RideService
    {
        private readonly Dictionary<Guid, Rider> riders = new();
        private readonly Dictionary<Guid, Ride> rides = new();
        private readonly DriverService driverService;
        private readonly object matchLock = new();

        public RideService(DriverService driverService)
        {
            this.driverService = driverService;
        }

        public Rider RegisterRider(string name)
        {
            var r = new Rider(name);
            riders[r.Id] = r;
            return r;
        }

        public Ride RequestRide(Guid riderId, Point pickup, Point destination)
        {
            if (!riders.TryGetValue(riderId, out var rider))
                throw new ArgumentException("Rider not found");

            var ride = new Ride(rider, pickup, destination);
            rides[ride.Id] = ride;

            var assigned = TryAssignNearestDriver(ride);
            return ride;
        }

        private bool TryAssignNearestDriver(Ride ride)
        {
            lock (matchLock)
            {
                var candidates = driverService.GetAvailableDrivers();
                if (!candidates.Any()) return false;

                var nearest = candidates.OrderBy(d => d.Location.DistanceTo(ride.Pickup)).FirstOrDefault();
                if (nearest == null) return false;

                var taken = nearest.TrySetUnavailable();
                if (!taken) return false;

                ride.Driver = nearest;
                ride.Status = RideStatus.Assigned;
                return true;
            }
        }

        public bool StartRide(Guid rideId, Guid driverId)
        {
            if (!rides.TryGetValue(rideId, out var ride)) return false;
            if (ride.Driver == null || ride.Driver.Id != driverId) return false;
            if (ride.Status != RideStatus.Assigned) return false;

            ride.Status = RideStatus.Started;
            ride.StartedAt = DateTime.UtcNow;
            return true;
        }

        public bool CompleteRide(Guid rideId, Guid driverId)
        {
            if (!rides.TryGetValue(rideId, out var ride)) return false;
            if (ride.Driver == null || ride.Driver.Id != driverId) return false;
            if (ride.Status != RideStatus.Started && ride.Status != RideStatus.Assigned) return false;

            ride.Status = RideStatus.Completed;
            ride.CompletedAt = DateTime.UtcNow;
            ride.Fare = CalculateFare(ride.Distance);

            ride.Rider.rideHistory.Add(ride);
            ride.Driver!.rideHistory.Add(ride);
            ride.Driver.SetAvailable();

            return true;
        }

        private double CalculateFare(double distance)
        {
            const double baseFare = 2.0;
            const double perUnit = 1.5;
            return Math.Round(baseFare + distance * perUnit, 2);
        }

        public IReadOnlyList<Ride> GetRiderHistory(Guid riderId)
        {
            if (!riders.TryGetValue(riderId, out var r)) return Array.Empty<Ride>();
            return r.RideHistory;
        }

        public Ride? GetRide(Guid id) => rides.TryGetValue(id, out var rv) ? rv : null;

        public IEnumerable<Rider> ListRiders() => riders.Values;

        public IEnumerable<Ride> ListRides() => rides.Values;
    }

    // Facade for demo
    public class CabSharingSystem
    {
        public DriverService DriverService { get; }
        public RideService RideService { get; }

        public CabSharingSystem()
        {
            DriverService = new DriverService();
            RideService = new RideService(DriverService);
        }
    }

    class Program
    {
        static void Main()
        {
            var system = new CabSharingSystem();
            var driverService = system.DriverService;
            var rideService = system.RideService;

            // Register drivers
            var d1 = driverService.RegisterDriver("Alice", new Point(0, 0));
            var d2 = driverService.RegisterDriver("Bob", new Point(5, 5));
            var d3 = driverService.RegisterDriver("Charlie", new Point(1, 2));

            // Register riders
            var r1 = rideService.RegisterRider("RiderOne");
            var r2 = rideService.RegisterRider("RiderTwo");

            Console.WriteLine("Drivers:");
            foreach (var d in driverService.ListDrivers())
                Console.WriteLine($" - {d.Name} at {d.Location} (available: {d.IsAvailable})");

            // RiderOne requests a ride
            var pickup = new Point(0.5, 0.5);
            var destination = new Point(10, 10);
            var ride = rideService.RequestRide(r1.Id, pickup, destination);

            Console.WriteLine($"\nRide requested by {r1.Name}: {ride}");

            if (ride.Driver != null)
            {
                Console.WriteLine($"Assigned driver: {ride.Driver.Name} at {ride.Driver.Location}");

                // Driver starts ride
                var started = rideService.StartRide(ride.Id, ride.Driver.Id);
                Console.WriteLine($"Start ride: {started}");

                // Simulate driver moving and completing ride
                driverService.UpdateDriverLocation(ride.Driver.Id, destination);

                var completed = rideService.CompleteRide(ride.Id, ride.Driver.Id);
                Console.WriteLine($"Complete ride: {completed}, Fare: {ride.Fare}");
            }
            else
            {
                Console.WriteLine("No driver available currently.");
            }

            // RiderTwo requests a ride where nearest driver should be someone else
            var ride2 = rideService.RequestRide(r2.Id, new Point(4.8, 4.9), new Point(6, 6));
            Console.WriteLine($"\nRide requested by {r2.Name}: {ride2}");
            if (ride2.Driver != null)
            {
                Console.WriteLine($"Assigned driver: {ride2.Driver.Name} at {ride2.Driver.Location}");
                rideService.StartRide(ride2.Id, ride2.Driver.Id);
                rideService.CompleteRide(ride2.Id, ride2.Driver.Id);
                Console.WriteLine($"Ride completed. Fare: {ride2.Fare}");
            }

            // Print histories
            Console.WriteLine($"\n{r1.Name} ride history:");
            foreach (var h in rideService.GetRiderHistory(r1.Id))
                Console.WriteLine($" - {h}");

            Console.WriteLine($"\n{d1.Name} ride history:");
            foreach (var h in driverService.GetDriverHistory(d1.Id))
                Console.WriteLine($" - {h}");

            Console.WriteLine($"\n{d2.Name} ride history:");
            foreach (var h in driverService.GetDriverHistory(d2.Id))
                Console.WriteLine($" - {h}");

            Console.WriteLine("\nDemo complete.");
        }
    }
}
