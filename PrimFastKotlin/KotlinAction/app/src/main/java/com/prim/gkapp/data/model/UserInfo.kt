package com.prim.gkapp.data.model

import android.util.Log
import com.google.gson.Gson
import com.prim.gkapp.AppContext
import com.prim.gkapp.exception.AppException
import com.prim.gkapp.network.OnLoginStateChangeListener
import com.prim.gkapp.network.entities.AuthBody
import com.prim.gkapp.network.entities.User
import com.prim.gkapp.network.service.AuthService
import com.prim.gkapp.network.service.UserService
import com.prim.gkapp.utils.Preference
import com.prim.gkapp.utils.fromJson
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import retrofit2.HttpException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * @desc 记录用户信息
 * @author prim
 * @time 2019-05-26 - 10:48
 * @version 1.0.0
 */
object UserInfo {
    //Preference 属性代理 默认会自动存入SharedPreference和自动取出数据
    //用户名
    var username: String by Preference(AppContext, "username", "")
    //密码
    var password: String by Preference(AppContext, "password", "")
    //token
    var token: String by Preference(AppContext, "token", "")
    //记录用户信息的json字符串
    var userInfoJson: String by Preference(AppContext, "userInfoJson", "")
    //记录鉴权ID
    var authId: Int by Preference(AppContext, "authId", 0)

    var firstIn: Boolean by Preference(AppContext, "firstIn", false)

    //判断是否登录
    fun isLogin(): Boolean = token.isNotEmpty()

    //监听登录状态
    val mOnStateListener = CopyOnWriteArrayList<OnLoginStateChangeListener>()

    //记录当前用户的User bean
    var currentUser: User? = null
        get() {
            if (field == null && userInfoJson.isNotEmpty()) {
                field = Gson().fromJson<User>(userInfoJson)
            }
            return field
        }
        set(value) {
            if (value == null) {
                userInfoJson = ""
            } else {
                userInfoJson = Gson().toJson(value)
            }
        }


    /**
     * 用户请求登录
     */
    fun login() = AuthService.createAuth(AuthBody())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeOn(Schedulers.io())
        .doOnNext {
            Log.e("login", it.toString())
            if (it.token.isEmpty()) AppException.AccountException(it)
        }
        .retryWhen { it ->
            //如果获取的token为空 则删除鉴权
            it.flatMap {
                Log.e("login", it.message)
                if (it is AppException.AccountException) {
                    AuthService.deleteAuth(it.response.id)//删除鉴权 然后再重新请求鉴权
                } else {
                    Observable.error(it)
                }
            }
        }.flatMap {
            //这时token肯定不为空 鉴权成功
            token = it.token
            authId = it.id
            UserService.authUser()//鉴权成功后 请求用户信息
        }.map {
            currentUser = it//存储用户信息
            notifyLogin(it)//回调监听
        }


    /**
     * 用户退出登录请求
     */
    fun logout() = AuthService.deleteAuth(authId)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeOn(Schedulers.io())
        .doOnNext {
            if (it.isSuccessful) {
                authId = -1
                token = ""
                currentUser = null
                notifyLogout()
            } else {
                throw HttpException(it)
            }
        }


    fun notifyLogin(user: User) {
        val listener: CopyOnWriteArrayList<OnLoginStateChangeListener> =
            mOnStateListener.clone() as CopyOnWriteArrayList<OnLoginStateChangeListener>
        listener.forEach {
            it.login(user)
        }
    }

    fun notifyLogout() {
        val listener: CopyOnWriteArrayList<OnLoginStateChangeListener> =
            mOnStateListener.clone() as CopyOnWriteArrayList<OnLoginStateChangeListener>
        listener.forEach {
            it.logout()
        }
    }


}