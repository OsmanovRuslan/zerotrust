/**
 * Движок принятия решения — Policy Engine в терминах NIST SP 800-207, часть PDP.
 *
 * <p>Состав: {@link ru.donstu.zerotrust.core.engine.TrustScoreCalculator} — формула оценки доверия,
 * {@link ru.donstu.zerotrust.core.engine.ThresholdResolver} — требуемый порог по формуле требуемого
 * порога,
 * {@link ru.donstu.zerotrust.core.engine.PolicyResolver} — слияние политики и переопределений
 * и {@link ru.donstu.zerotrust.core.engine.DecisionEngine} — алгоритм решения.
 *
 * <p>Как и весь {@code zerotrust-core}, пакет не зависит от Spring: решение — это чистая
 * функция от контекста вызова, значений факторов и действующей политики, и проверяется она
 * обычными unit-тестами без поднятия контекста приложения. Проверки шагов 1–5 алгоритма
 * (блокировки, устройство, роли, владелец) делают компоненты слоя autoconfigure и передают
 * движку готовый итог в {@link ru.donstu.zerotrust.core.engine.DecisionRequest}.
 */
package ru.donstu.zerotrust.core.engine;
