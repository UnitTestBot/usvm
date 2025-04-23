/*
 * Copyright (c) 2021-2023 Huawei Device Co., Ltd.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * @file
 * @kit ArkUI
 */
/**
 * Provides a way to control the process.
 *
 * @since 8
 */
/**
 * Provides a way to control the process.
 *
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @since 10
 */
/**
 * Provides a way to control the process.
 *
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @atomicservice
 * @since 11
 */
declare class TextTimerController {
    /**
     * constructor.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 8
     */
    /**
     * constructor.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * constructor.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    constructor();
    /**
     * Provides a start event for timer.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 8
     */
    /**
     * Provides a start event for timer.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Provides a start event for timer.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    start();
    /**
     * Provides a pause event for timer.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 8
     */
    /**
     * Provides a pause event for timer.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Provides a pause event for timer.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    pause();
    /**
     * Provides an event to reset timer.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 8
     */
    /**
     * Provides an event to reset timer.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Provides an event to reset timer.
     *
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    reset();
}
/**
 * TextTimerConfiguration used by content modifier.
 *
 * @interface TextTimerConfiguration
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @atomicservice
 * @since 12
 */
declare interface TextTimerConfiguration extends CommonConfiguration<TextTimerConfiguration> {
    /**
     * Specifies the timer range.
     * In the non-countDown scenario, a negative value indicates that the timer is not limited.
     * The unit is millisecond.
     *
     * @type { number }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 12
     */
    count: number;
    /**
     * Texttimer is isCountDown or not.
     *
     * @type { boolean }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 12
     */
    isCountDown: boolean;
    /**
     * Texttimer is started or not.
     *
     * @type { boolean }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 12
     */
    started: boolean;
    /**
     * Elapsed time of the timer, readonly.
     * The unit is millisecond.
     *
     * @type { number }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 12
     */
    elapsedTime: number;
}
/**
 * Defines the options of TextTimer.
 *
 * @interface TextTimerOptions
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @since 8
 */
/**
 * Defines the options of TextTimer.
 *
 * @interface TextTimerOptions
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @since 10
 */
/**
 * Defines the options of TextTimer.
 *
 * @interface TextTimerOptions
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @atomicservice
 * @since 11
 */
interface TextTimerOptions {
    /**
     * Sets whether to countdown.The default value is false.
     *
     * @type { ?boolean }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 8
     */
    /**
     * Sets whether to countdown.The default value is false.
     *
     * @type { ?boolean }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Sets whether to countdown.The default value is false.
     *
     * @type { ?boolean }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    isCountDown?: boolean;
    /**
     * Specifies the timer range.
     * In the non-countDown scenario, a negative value indicates that the timer is not limited.
     * The unit is millisecond.
     *
     * @type { ?number }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 8
     */
    /**
     * Specifies the timer range.
     * In the non-countDown scenario, a negative value indicates that the timer is not limited.
     * The unit is millisecond.
     *
     * @type { ?number }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Specifies the timer range.
     * In the non-countDown scenario, a negative value indicates that the timer is not limited.
     * The unit is millisecond.
     *
     * @type { ?number }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    count?: number;
    /**
     * Controller of Texttimer.
     *
     * @type { ?TextTimerController }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 8
     */
    /**
     * Controller of Texttimer.
     *
     * @type { ?TextTimerController }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Controller of Texttimer.
     *
     * @type { ?TextTimerController }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    controller?: TextTimerController;
}
/**
 * Provides an interface for texttimer containers.
 *
 * @interface TextTimerInterface
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @since 8
 */
/**
 * Provides an interface for texttimer containers.
 *
 * @interface TextTimerInterface
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @since 10
 */
/**
 * Provides an interface for texttimer containers.
 *
 * @interface TextTimerInterface
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @atomicservice
 * @since 11
 */
interface TextTimerInterface {
    /**
     * Defines the TextTimer constructor.
     *
     * @param { TextTimerOptions } options
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 8
     */
    /**
     * Defines the TextTimer constructor.
     *
     * @param { TextTimerOptions } options
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Defines the TextTimer constructor.
     *
     * @param { TextTimerOptions } options
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    (options?: TextTimerOptions): TextTimerAttribute;
}
/**
 * Defines the TextTimer attribute functions.
 *
 * @extends CommonMethod<TextTimerAttribute>
 * @since 8
 */
/**
 * Defines the TextTimer attribute functions.
 *
 * @extends CommonMethod<TextTimerAttribute>
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @since 10
 */
/**
 * Defines the TextTimer attribute functions.
 *
 * @extends CommonMethod<TextTimerAttribute>
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @atomicservice
 * @since 11
 */
declare class TextTimerAttribute extends CommonMethod<TextTimerAttribute> {
    /**
     * Set the display time format, for example, now is hh/mm/ss/ms and current: hh-mm-ss-ms.
     * The time format string can be hh, mm, ss, or ms.
     *
     * @param { string } value
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 8
     */
    /**
     * Set the display time format, for example, now is hh/mm/ss/ms and current: hh-mm-ss-ms.
     * The time format string can be hh, mm, ss, or ms.
     *
     * @param { string } value
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Set the display time format, for example, now is hh/mm/ss/ms and current: hh-mm-ss-ms.
     * The time format string can be hh, mm, ss, or ms.
     *
     * @param { string } value
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    format(value: string): TextTimerAttribute;
    /**
     * Called when the font color is set.
     *
     * @param { ResourceColor } value
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 8
     */
    /**
     * Called when the font color is set.
     *
     * @param { ResourceColor } value
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Called when the font color is set.
     *
     * @param { ResourceColor } value
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    fontColor(value: ResourceColor): TextTimerAttribute;
    /**
     * Called when the font size is set.
     *
     * @param { Length } value
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 8
     */
    /**
     * Called when the font size is set.
     *
     * @param { Length } value
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Called when the font size is set.
     *
     * @param { Length } value
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    fontSize(value: Length): TextTimerAttribute;
    /**
     * Called when the fontStyle is set
     *
     * @param { FontStyle } value
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 8
     */
    /**
     * Called when the fontStyle is set
     *
     * @param { FontStyle } value
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Called when the fontStyle is set
     *
     * @param { FontStyle } value
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    fontStyle(value: FontStyle): TextTimerAttribute;
    /**
     * Called when the fontWeight is set
     *
     * @param { number | FontWeight | string } value
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 8
     */
    /**
     * Called when the fontWeight is set
     *
     * @param { number | FontWeight | string } value
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Called when the fontWeight is set
     *
     * @param { number | FontWeight | string } value
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    fontWeight(value: number | FontWeight | string): TextTimerAttribute;
    /**
     * Called when the fontFamily is set
     *
     * @param { ResourceStr } value
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 8
     */
    /**
     * Called when the fontFamily is set
     *
     * @param { ResourceStr } value
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Called when the fontFamily is set
     *
     * @param { ResourceStr } value
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    fontFamily(value: ResourceStr): TextTimerAttribute;
    /**
     * Called when the timer value is returned.
     *
     * @param { function } event
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @since 8
     */
    /**
     * Called when the timer value is returned.
     *
     * @param { function } event
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @since 10
     */
    /**
     * Called when the timer value is returned.
     *
     * @param { function } event
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @form
     * @atomicservice
     * @since 11
     */
    onTimer(event: (utc: number, elapsedTime: number) => void): TextTimerAttribute;
    /**
     * Called when the text shadow is set.
     *
     * @param { ShadowOptions | Array<ShadowOptions> } value - The shadow options.
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @since 11
     */
    /**
     * Called when the text shadow is set.
     *
     * @param { ShadowOptions | Array<ShadowOptions> } value - The shadow options.
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 12
     */
    textShadow(value: ShadowOptions | Array<ShadowOptions>): TextTimerAttribute;
    /**
     * Set the content modifier of texttimer.
     *
     * @param { ContentModifier<TextTimerConfiguration> } modifier - The content modifier of texttimer.
     * @returns { TextTimerAttribute }
     * @syscap SystemCapability.ArkUI.ArkUI.Full
     * @crossplatform
     * @atomicservice
     * @since 12
     */
    contentModifier(modifier: ContentModifier<TextTimerConfiguration>): TextTimerAttribute;
}
/**
 * Defines TextTimer Component.
 *
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @since 8
 */
/**
 * Defines TextTimer Component.
 *
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @since 10
 */
/**
 * Defines TextTimer Component.
 *
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @atomicservice
 * @since 11
 */
declare const TextTimer: TextTimerInterface;
/**
 * Defines TextTimer Component instance.
 *
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @since 8
 */
/**
 * Defines TextTimer Component instance.
 *
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @since 10
 */
/**
 * Defines TextTimer Component instance.
 *
 * @syscap SystemCapability.ArkUI.ArkUI.Full
 * @crossplatform
 * @form
 * @atomicservice
 * @since 11
 */
declare const TextTimerInstance: TextTimerAttribute;
