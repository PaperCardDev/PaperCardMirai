package cn.paper_card.mirai;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.Dispatchers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.utils.DeviceVerificationRequests;
import net.mamoe.mirai.utils.DeviceVerificationResult;
import net.mamoe.mirai.utils.LoginSolver;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class TheLoginSolver2 extends LoginSolver {

    private final @NotNull CommandSender sender;
    private final long qq;

    private final @NotNull Object sliderLock;

    private final @NotNull Object smsLock;

    private boolean waitingSliderVerify = false;
    private boolean waitingSmsCode = false;

    private @Nullable Object sliderResult = null; // 滑块验证结果
    private @Nullable String smsCode = null; // 短信验证码

    TheLoginSolver2(@NotNull CommandSender sender, long qq) {
        this.sender = sender;
        this.qq = qq;
        this.sliderLock = new Object();
        this.smsLock = new Object();
    }

    boolean isWaitingSliderVerify() {
        synchronized (this.sliderLock) {
            return this.waitingSliderVerify;
        }
    }

    boolean isWaitingSmsCode() {
        synchronized (this.smsLock) {
            return this.waitingSmsCode;
        }
    }

    long getQq() {
        return this.qq;
    }

    @Override
    public @Nullable Object onSolvePicCaptcha(@NotNull Bot bot, byte @NotNull [] bytes, @NotNull Continuation<? super String> continuation) {
        throw new UnsupportedOperationException("目前尚未支持图片验证码");
    }


    @Override
    public @Nullable Object onSolveSliderCaptcha(@NotNull Bot bot, @NotNull String s, @NotNull Continuation<? super String> continuation) {

        this.sender.sendMessage(Component.text("请手动处理滑块验证码完成人机验证..."));
        this.sender.sendMessage(Component.text()
                .append(Component.text("验证链接（可点击打开）："))
                .append(Component.text(s).decorate(TextDecoration.UNDERLINED).color(NamedTextColor.GREEN).clickEvent(ClickEvent.openUrl(s)))
                .build());


        this.sender.sendMessage(Component.text("等待滑块验证完成，请在三分钟之内完成验证..."));

        synchronized (this.sliderLock) {
            this.waitingSliderVerify = true;
            try {
                this.sliderLock.wait(3 * 60 * 1000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            this.waitingSliderVerify = false;
        }

        return this.sliderResult;
    }

    void setSliderResultAndNotify(@Nullable Object result) {
        synchronized (this.sliderLock) {
            this.sliderResult = result;
            this.sliderLock.notifyAll();
        }
    }

    void setSmsResultAndNotify(@Nullable String code) {
        synchronized (this.smsLock) {
            this.smsCode = code;
            this.smsLock.notifyAll();
        }
    }

    void notifyClose() {
        synchronized (this.sliderLock) {
            this.sliderLock.notifyAll();
        }

        synchronized (this.smsLock) {
            this.smsLock.notifyAll();
        }
    }

    @Override
    public @Nullable Object onSolveDeviceVerification(@NotNull Bot bot, @NotNull DeviceVerificationRequests requests, @NotNull Continuation<? super DeviceVerificationResult> $completion) {

        final DeviceVerificationRequests.SmsRequest sms = requests.getSms();

        if (sms == null) {
            final String error = "不支持使用短信验证码进行验证！";
            this.sender.sendMessage(Component.text(error).color(NamedTextColor.DARK_RED));
            throw new UnsupportedOperationException(error);
        }


        this.sender.sendMessage(Component.text("将短信验证码发送到您的手机[%s-%s]上...".formatted(sms.getCountryCode(), sms.getPhoneNumber())));
        sms.requestSms(new Continuation<>() {
            @Override
            public @NotNull CoroutineContext getContext() {
                return (CoroutineContext) Dispatchers.getIO();
            }

            @Override
            public void resumeWith(@NotNull Object o) {
            }
        });


        // 等待短信验证码
        synchronized (this.smsLock) {
            this.waitingSmsCode = true;
            try {
                this.smsLock.wait(3 * 60 * 1000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            this.waitingSmsCode = false;
        }

        if (this.smsCode != null)
            return sms.solved(this.smsCode);
        else return null;
    }
}
