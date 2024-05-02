package com.example.RideIt.service;

import com.example.RideIt.dto.request.TripBookingRequest;
import com.example.RideIt.dto.response.TripBookingResponse;
import com.example.RideIt.exception.CabNotAvailableException;
import com.example.RideIt.exception.CustomerNotFoundException;
import com.example.RideIt.model.Cab;
import com.example.RideIt.model.Customer;
import com.example.RideIt.model.TripBooking;
import com.example.RideIt.repository.CabRepository;
import com.example.RideIt.repository.CustomerRepository;
import com.example.RideIt.repository.DriverRepository;
import com.example.RideIt.repository.TripBookingRepository;
import com.example.RideIt.transformer.BookingTransformer;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TripBookingService {

    private final CustomerRepository customerRepository;
    private final CabRepository cabRepository;
    private final DriverRepository driverRepository;
    private final TripBookingRepository tripBookingRepository;

    @Autowired
     JavaMailSender javaMailSender;

    public TripBookingResponse bookCab(boolean applyCoupon,
                                       @NotNull TripBookingRequest tripBookingRequest) {

        // validate email id of the customer
        Customer customer = customerRepository.findByEmailId(tripBookingRequest
                .getCustomerEmailId());

        if(customer == null) {
            throw new CustomerNotFoundException("Invalid email id");
        }

        // check if a cab is available or not for booking
        // get a random available cab
        Cab cab = cabRepository.getRandomAvailableCab();
        if(cab == null) {
            throw new CabNotAvailableException("Sorry! All drivers are busy right now!!");
        }

        // prepare the booking entity
        TripBooking tripBooking = BookingTransformer.bookingRequestToBooking(tripBookingRequest);
        tripBooking.setTotalFare(cab.getFarePerKm() * tripBookingRequest.getTripDistanceInKm());
        tripBooking.setCustomer(customer);
        tripBooking.setDriver(cab.getDriver());

        // save booking
        TripBooking savedTripBooking = tripBookingRepository.save(tripBooking);

        customer.getBookings().add(savedTripBooking);
        cab.setAvailable(false);
        cab.getDriver().getBookings().add(savedTripBooking);

        // customer and booking
        customerRepository.save(customer); // customer + savedBooking
        driverRepository.save(cab.getDriver()); // driver + cab + savedBooking

        sendEmail(savedTripBooking);

        // last step -> prepare booking response
        return BookingTransformer.tripBookingToTripBookingResponse(savedTripBooking);

    }

    private void sendEmail(TripBooking savedTripBooking) {

        // prepare your email
        String text = "Congrats!! " + savedTripBooking.getCustomer().getName()
                + " your ride is booked with" + savedTripBooking.getDriver().getName();

        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setFrom("acciojobspring@gmail.com");
        simpleMailMessage.setTo(savedTripBooking.getCustomer().getEmailId());
        simpleMailMessage.setSubject("Cab Booked!!!");
        simpleMailMessage.setText(text);

        // send the email
        javaMailSender.send(simpleMailMessage);
    }
}
