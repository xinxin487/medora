package com.medora.appointment.controller;

import com.medora.appointment.waitlist.WaitlistService;
import com.medora.auth.repository.UserAccountRepository;
import com.medora.auth.security.CurrentAuthenticatedUserProvider;
import com.medora.common.api.ApiResponse;
import com.medora.common.exception.BusinessException;
import com.medora.masterdata.repository.ScheduleSlotRepository;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/waitlist")
public class WaitlistController {
 private final WaitlistService service; private final CurrentAuthenticatedUserProvider auth; private final UserAccountRepository users; private final ScheduleSlotRepository slots;
 public WaitlistController(WaitlistService service,CurrentAuthenticatedUserProvider auth,UserAccountRepository users,ScheduleSlotRepository slots){this.service=service;this.auth=auth;this.users=users;this.slots=slots;}
 @PostMapping public ApiResponse<Entry> join(@RequestBody JoinRequest request){
  Long userId=currentUserId(); var slot=slots.findByPublicId(UUID.fromString(request.slotId())).orElseThrow(()->new BusinessException("MASTER_SLOT_NOT_FOUND",HttpStatus.NOT_FOUND,"schedule slot not found"));
  var entry=service.join(userId,slot.getId(),request.priority()==null?0:request.priority()); return ApiResponse.success(new Entry(entry.getPublicId().toString(),entry.getStatus()));
 }
 @PostMapping("/{entryId}/cancel") public ApiResponse<Void> cancel(@PathVariable String entryId){service.cancel(currentUserId(),UUID.fromString(entryId));return ApiResponse.success(null);}
 private Long currentUserId(){return users.findByPublicId(auth.getRequiredUser().userPublicId()).orElseThrow(()->new BusinessException("USER_NOT_FOUND",HttpStatus.NOT_FOUND,"current user not found")).getId();}
 public record JoinRequest(@NotBlank String slotId,Integer priority){} public record Entry(String entryId,String status){}
}
