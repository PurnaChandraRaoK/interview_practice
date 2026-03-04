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

    // Core system for cab sharing
    public class CabSharingSystem
    {
        private readonly Dictionary<Guid, Driver> drivers = new();
        private readonly Dictionary<Guid, Rider> riders = new();
        private readonly Dictionary<Guid, Ride> rides = new();
        private readonly object matchLock = new();

        // registration
        public Driver RegisterDriver(string name, Point initialLocation)
        {
            var d = new Driver(name, initialLocation);
            drivers[d.Id] = d;
            return d;
        }

        public Rider RegisterRider(string name)
        {
            var r = new Rider(name);
            riders[r.Id] = r;
            return r;
        }

        public bool UpdateDriverLocation(Guid driverId, Point newLocation)
        {
            if (!drivers.TryGetValue(driverId, out var d)) return false;
            d.UpdateLocation(newLocation);
            return true;
        }

        // Rider requests a ride. System finds nearest available driver and assigns.
        public Ride RequestRide(Guid riderId, Point pickup, Point destination)
        {
            if (!riders.TryGetValue(riderId, out var rider))
                throw new ArgumentException("Rider not found");

            var ride = new Ride(rider, pickup, destination);
            rides[ride.Id] = ride;

            // Try to match with nearest available driver
            var assigned = TryAssignNearestDriver(ride);
            if (!assigned)
            {
                // leave ride as Requested. Could implement retry/queueing.
            }

            return ride;
        }

        // Finds nearest available driver and assigns atomically
        private bool TryAssignNearestDriver(Ride ride)
        {
            lock (matchLock)
            {
                var candidates = drivers.Values.Where(d => d.IsAvailable).ToList();
                if (!candidates.Any()) return false;

                var nearest = candidates
                    .OrderBy(d => d.Location.DistanceTo(ride.Pickup))
                    .FirstOrDefault();

                if (nearest == null) return false;

                // Atomically mark driver unavailable
                var taken = nearest.TrySetUnavailable();
                if (!taken) return false;

                ride.Driver = nearest;
                ride.Status = RideStatus.Assigned;

                // Add to temporary places, histories will be finalized at completion
                return true;
            }
        }

        // Driver starts the ride
        public bool StartRide(Guid rideId, Guid driverId)
        {
            if (!rides.TryGetValue(rideId, out var ride)) return false;
            if (ride.Driver == null || ride.Driver.Id != driverId) return false;
            if (ride.Status != RideStatus.Assigned) return false;

            ride.Status = RideStatus.Started;
            ride.StartedAt = DateTime.UtcNow;
            return true;
        }

        // Complete ride: calculate fare, mark driver available, save history
        public bool CompleteRide(Guid rideId, Guid driverId)
        {
            if (!rides.TryGetValue(rideId, out var ride)) return false;
            if (ride.Driver == null || ride.Driver.Id != driverId) return false;
            if (ride.Status != RideStatus.Started && ride.Status != RideStatus.Assigned) return false;

            ride.Status = RideStatus.Completed;
            ride.CompletedAt = DateTime.UtcNow;

            // Simple fare: base + per km
            ride.Fare = CalculateFare(ride.Distance);

            // Update histories
            ride.Rider.rideHistory.Add(ride);
            ride.Driver!.rideHistory.Add(ride);

            // Driver becomes available again
            ride.Driver.SetAvailable();

            return true;
        }

        private double CalculateFare(double distance)
        {
            const double baseFare = 2.0; // base
            const double perUnit = 1.5; // per distance unit
            return Math.Round(baseFare + distance * perUnit, 2);
        }

        public IReadOnlyList<Ride> GetRiderHistory(Guid riderId)
        {
            if (!riders.TryGetValue(riderId, out var r)) return Array.Empty<Ride>();
            return r.RideHistory;
        }

        public IReadOnlyList<Ride> GetDriverHistory(Guid driverId)
        {
            if (!drivers.TryGetValue(driverId, out var d)) return Array.Empty<Ride>();
            return d.RideHistory;
        }

        public Driver? GetDriver(Guid id) => drivers.TryGetValue(id, out var d) ? d : null;
        public Rider? GetRider(Guid id) => riders.TryGetValue(id, out var r) ? r : null;
        public Ride? GetRide(Guid id) => rides.TryGetValue(id, out var rv) ? rv : null;

        // For demo / monitoring
        public IEnumerable<Driver> ListDrivers() => drivers.Values;
        public IEnumerable<Rider> ListRiders() => riders.Values;
        public IEnumerable<Ride> ListRides() => rides.Values;
    }

    class Program
    {
        static void Main()
        {
            var system = new CabSharingSystem();

            // Register drivers
            var d1 = system.RegisterDriver("Alice", new Point(0, 0));
            var d2 = system.RegisterDriver("Bob", new Point(5, 5));
            var d3 = system.RegisterDriver("Charlie", new Point(1, 2));

            // Register riders
            var r1 = system.RegisterRider("RiderOne");
            var r2 = system.RegisterRider("RiderTwo");

            Console.WriteLine("Drivers:");
            foreach (var d in system.ListDrivers())
                Console.WriteLine($" - {d.Name} at {d.Location} (available: {d.IsAvailable})");

            // RiderOne requests a ride
            var pickup = new Point(0.5, 0.5);
            var destination = new Point(10, 10);
            var ride = system.RequestRide(r1.Id, pickup, destination);

            Console.WriteLine($"\nRide requested by {r1.Name}: {ride}");

            if (ride.Driver != null)
            {
                Console.WriteLine($"Assigned driver: {ride.Driver.Name} at {ride.Driver.Location}");

                // Driver starts ride
                var started = system.StartRide(ride.Id, ride.Driver.Id);
                Console.WriteLine($"Start ride: {started}");

                // Simulate driver moving and completing ride
                system.UpdateDriverLocation(ride.Driver.Id, destination);

                var completed = system.CompleteRide(ride.Id, ride.Driver.Id);
                Console.WriteLine($"Complete ride: {completed}, Fare: {ride.Fare}");
            }
            else
            {
                Console.WriteLine("No driver available currently.");
            }

            // RiderTwo requests a ride where nearest driver should be someone else
            var ride2 = system.RequestRide(r2.Id, new Point(4.8, 4.9), new Point(6, 6));
            Console.WriteLine($"\nRide requested by {r2.Name}: {ride2}");
            if (ride2.Driver != null)
            {
                Console.WriteLine($"Assigned driver: {ride2.Driver.Name} at {ride2.Driver.Location}");
                system.StartRide(ride2.Id, ride2.Driver.Id);
                system.CompleteRide(ride2.Id, ride2.Driver.Id);
                Console.WriteLine($"Ride completed. Fare: {ride2.Fare}");
            }

            // Print histories
            Console.WriteLine($"\n{r1.Name} ride history:");
            foreach (var h in system.GetRiderHistory(r1.Id))
                Console.WriteLine($" - {h}");

            Console.WriteLine($"\n{d1.Name} ride history:");
            foreach (var h in system.GetDriverHistory(d1.Id))
                Console.WriteLine($" - {h}");

            Console.WriteLine($"\n{d2.Name} ride history:");
            foreach (var h in system.GetDriverHistory(d2.Id))
                Console.WriteLine($" - {h}");

            Console.WriteLine("\nDemo complete.");
        }
    }
}
