package ru.donstu.zerotrust.core.spi;

import ru.donstu.zerotrust.core.model.RuleOutcome;
import ru.donstu.zerotrust.core.rule.RuleContext;

import java.lang.annotation.Annotation;

/**
 * Обработчик правила условий: по аннотации и контексту вызова решает, сработало ли правило.
 *
 * <p>Так устроены и встроенные правила модуля, и свои правила приложения: аннотация
 * помечается {@code @TrustRuleAnnotation(evaluator = ...)}, а обработчик объявляется бином.
 *
 * @param <A> тип аннотации-правила
 */
public interface RuleEvaluator<A extends Annotation> {

    /**
     * Исход правила: {@code RuleOutcome.pass(id)}, если правило не сработало.
     */
    RuleOutcome evaluate(A annotation, RuleContext context);
}
