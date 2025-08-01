package com.example.microservice.Services;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.microservice.DTO.Request.UserRequest;
import com.example.microservice.DTO.Response.Hotel;
import com.example.microservice.DTO.Response.Rating;
import com.example.microservice.DTO.Response.UserResponse;
import com.example.microservice.Entities.User;
import com.example.microservice.Repositories.UserRepository;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private HotelService hotelService;

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    // Create a new user
    public User createUser(UserRequest userPayload) {
        User user = new User();
        user.setEmail(userPayload.getEmail());
        user.setName(userPayload.getName());
        user.setPassword(userPayload.getPassword());
        userRepository.save(user);
        return user;
    }

    // Greet user by ID
    public String greetUser(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));
        return "Welcome " + user.getName();
    }

    // Delete user by ID
    public String deleteUser(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));
        userRepository.deleteById(id);
        return user.getName() + " Deleted";
    }

    // Update user details
    public UserResponse updateUser(String id, UserRequest userUpdatePayload) {
        UserResponse userResponse = new UserResponse();

        Optional<User> exUser = userRepository.findById(id);
        if (exUser.isPresent()) {
            User user = exUser.get();
            if (userUpdatePayload.getName() != null)
                user.setName(userUpdatePayload.getName());
            if (userUpdatePayload.getEmail() != null)
                user.setEmail(userUpdatePayload.getEmail());
            if (userUpdatePayload.getPassword() != null)
                user.setPassword(userUpdatePayload.getPassword());
            userRepository.save(user);

            userResponse.setEmail(user.getEmail());
            userResponse.setName(user.getName());
        }
        return userResponse;
    }

    // Fetch user with ratings and hotel details
    @CircuitBreaker(name = "getUserEntity", fallbackMethod = "userEntityFallback")
    public Optional<User> getUserEntity(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));

        // Fetch ratings for user
        Rating[] ratingsArray = restTemplate.getForObject(
                "http://RATINGSERVICE/ratings/user/" + id,
                Rating[].class);

        if (ratingsArray != null) {
            List<Rating> enrichedRatings = Arrays.stream(ratingsArray)
                    .map(rating -> {
                        try {
                            Hotel hotel = hotelService.getHotel(rating.getHotelId());
                            rating.setHotel(hotel);
                        } catch (Exception e) {
                            logger.warn("Failed to fetch hotel for rating with hotelId: {}", rating.getHotelId(), e);
                            rating.setHotel(null);
                        }
                        return rating;
                    }).collect(Collectors.toList());

            user.setRatings(enrichedRatings);
        } else {
            logger.warn("No ratings found for user ID: {}", id);
            user.setRatings(List.of());
        }

        return Optional.of(user);
    }

    // Fallback method for circuit breaker
    public Optional<User> userEntityFallback(String id, Exception ex) {
        logger.warn("Fallback triggered for getUserEntity(), userId: {}. Reason: {}", id, ex.toString());

        User fallbackUser = new User();
        fallbackUser.setName("Unknown");
        fallbackUser.setEmail("unknown@email.com");
        fallbackUser.setRatings(List.of());

        return Optional.of(fallbackUser);
    }
}
