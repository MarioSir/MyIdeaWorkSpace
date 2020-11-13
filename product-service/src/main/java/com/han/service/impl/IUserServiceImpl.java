package com.han.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.han.dao.IUserDao;
import com.han.entity.User;
import com.han.service.IUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IUserServiceImpl implements IUserService {
    private static final Logger logger = LoggerFactory.getLogger(IUserServiceImpl.class);
    @Autowired
    private IUserDao userDao;

    @Override
    public User findUserByUserId(Integer userId) {
        logger.info("查询用户【{}】数据", userId);
        final User user = userDao.getByUserId(userId);
        logger.info("查询数据库用户数据【{}】", JSONObject.toJSONString(user));
        return user;
    }

    @Override
    public List<Integer> findAllUserIds() {
        return userDao.findAllUserIds();
    }
}
