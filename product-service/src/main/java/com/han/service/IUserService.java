package com.han.service;

import com.han.entity.User;

import java.util.List;

public interface IUserService {

    public User findUserByUserId(Integer userId);

    List<Integer> findAllUserIds();
}
