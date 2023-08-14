package cn.paper_card.mirai;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.Dispatchers;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.utils.DeviceVerificationRequests;
import net.mamoe.mirai.utils.DeviceVerificationResult;
import net.mamoe.mirai.utils.LoginSolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Set;

class TheLoginSolver extends LoginSolver {

    private final @NotNull PaperCardMirai plugin;

    private final @NotNull HashMap<Long, Object> sliderLocks = new HashMap<>();

    private final @NotNull HashMap<Long, Object> smsLocks = new HashMap<>();

    private final @NotNull HashMap<Long, Object> sliderResults = new HashMap<>();

    private final @NotNull HashMap<Long, String> smsResults = new HashMap<>();

    TheLoginSolver(@NotNull PaperCardMirai plugin) {
        this.plugin = plugin;
    }


    @Override
    public @Nullable Object onSolvePicCaptcha(@NotNull Bot bot, byte @NotNull [] bytes, @NotNull Continuation<? super String> continuation) {
        throw new UnsupportedOperationException("目前尚未支持图片验证码");
    }


    @Override
    public @Nullable Object onSolveSliderCaptcha(@NotNull Bot bot, @NotNull String s, @NotNull Continuation<? super String> continuation) {
        this.plugin.getLogger().info("请手动处理滑块验证码...");
        this.plugin.getLogger().info("验证链接: " + s);

        final Object lock;
        synchronized (this.sliderLocks) {
            lock = this.sliderLocks.computeIfAbsent(bot.getId(), k -> new Object());
        }

        this.plugin.getLogger().info("等待滑块验证完成...");
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        final Object result;
        synchronized (this.sliderResults) {
            result = this.sliderResults.remove(bot.getId());
        }

        // 移除锁
        synchronized (this.sliderLocks) {
            this.sliderLocks.remove(bot.getId());
        }

        return result;
    }

    public void setSliderResultAndNotify(long botId, @Nullable Object result) {
        synchronized (this.sliderResults) {
            this.sliderResults.put(botId, result);
        }

        final Object lock;
        synchronized (this.sliderLocks) {
            lock = this.sliderLocks.get(botId);
        }

        if (lock == null) return;

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    public void setSmsResultAndNotify(long botId, @Nullable String code) {
        synchronized (this.smsResults) {
            this.smsResults.put(botId, code);
        }

        final Object lock;

        synchronized (this.smsLocks) {
            lock = this.smsLocks.get(botId);
        }

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    public @NotNull Set<Long> getWaitingSliderIds() {
        synchronized (this.sliderLocks) {
            return this.sliderLocks.keySet();
        }
    }

    public Set<Long> getWaitingSmsIds() {
        synchronized (this.smsLocks) {
            return this.smsLocks.keySet();
        }
    }


    @Override
    public @Nullable Object onSolveDeviceVerification(@NotNull Bot bot, @NotNull DeviceVerificationRequests requests, @NotNull Continuation<? super DeviceVerificationResult> $completion) {

        final DeviceVerificationRequests.SmsRequest sms = requests.getSms();

        if (sms == null) throw new UnsupportedOperationException("不能支持使用短信验证码进行验证！");

        this.plugin.getLogger().info("将短信验证码发送到您的手机[%s-%s]上...".formatted(sms.getCountryCode(), sms.getPhoneNumber()));
        sms.requestSms(new Continuation<>() {
            @Override
            public @NotNull CoroutineContext getContext() {
                return (CoroutineContext) Dispatchers.getIO();
            }

            @Override
            public void resumeWith(@NotNull Object o) {
            }
        });

        final Object lock;
        synchronized (this.smsLocks) {
            lock = this.smsLocks.computeIfAbsent(bot.getId(), k -> new Object());
        }

        // 等待短信验证码
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        final String code;
        synchronized (this.smsResults) {
            code = this.smsResults.remove(bot.getId());
        }

        // 移除锁
        synchronized (this.smsLocks) {
            this.smsLocks.remove(bot.getId());
        }

        return sms.solved(code);
    }
}
