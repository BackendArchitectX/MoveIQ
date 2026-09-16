import type { DecisionDossier } from './api'

type Props = {
    dossier: DecisionDossier | null
    error?: string | null
}

function fmt(
    value: number | null,
    suffix = ''
) {
    return value == null
        ? '—'
        : `${value.toFixed(1)}${suffix}`
}

function words(
    value: string | null | undefined
) {
    return value
        ? value.split('_').join(' ')
        : '—'
}

export function SensePanel({
                               dossier,
                               error
                           }: Props) {
    if (!dossier) {
        return (
            <article className="stage-card stage-sense stage-waiting">
                <div className="stage-heading">
                    <span>SENSE</span>
                    <span className="stage-status">
                        {error ? 'DEGRADED' : 'WAITING'}
                    </span>
                </div>

                <p className="waiting-copy">
                    {error ??
                        'Waiting for the first durable decision case.'}
                </p>
            </article>
        )
    }

    const sense = dossier.sense

    const detectionCount =
        sense.detectedWindowEventCount ??
        sense.windowEventCount ??
        0

    const threshold =
        sense.threshold ?? 0

    const progress =
        threshold > 0
            ? Math.min(
                100,
                (detectionCount / threshold) * 100
            )
            : 0

    const steps =
        threshold > 0
            ? Math.min(threshold, 12)
            : 0

    return (
        <article className="stage-card stage-sense stage-active">
            <div className="stage-heading">
                <span>SENSE</span>

                <span
                    className={
                        `stage-status ${
                            sense.thresholdCrossed
                                ? 'status-detected'
                                : ''
                        }`
                    }
                >
                    {sense.thresholdCrossed
                        ? 'THRESHOLD CROSSED'
                        : 'SENSING'}
                </span>
            </div>

            <div className="sense-window-row">
                <div>
                    <span className="sense-label">
                        DETECTION POINT
                    </span>

                    <strong className="sense-count">
                        {detectionCount} / {threshold || '—'}
                    </strong>
                </div>

                <span className="sense-time">
                    CURRENT WINDOW {sense.windowEventCount ?? '—'}
                </span>
            </div>

            <div
                className="sense-progress"
                role="progressbar"
                aria-label="Detection threshold progress"
                aria-valuemin={0}
                aria-valuemax={threshold || 0}
                aria-valuenow={detectionCount}
            >
                <span
                    style={{
                        width: `${progress}%`
                    }}
                />
            </div>

            {steps > 0 && (
                <div
                    className="sense-steps"
                    aria-hidden="true"
                >
                    {Array.from({
                        length: steps
                    }).map((_, index) => (
                        <span
                            key={index}
                            className={
                                index < detectionCount
                                    ? 'sense-step active'
                                    : 'sense-step'
                            }
                        />
                    ))}
                </div>
            )}

            <div className="sense-metrics">
                <div>
                    <span>Affected at detection</span>
                    <strong>
                        {sense.affectedEmployeeTrips ?? '—'}
                    </strong>
                </div>

                <div>
                    <span>Delay evidence</span>
                    <strong>
                        {sense.delayMinutesSum == null
                            ? '—'
                            : `${sense.delayMinutesSum}m`}
                    </strong>
                </div>
            </div>

            <p
                className="sense-scope"
                title={dossier.caseInfo.scopeKey}
            >
                {dossier.caseInfo.scopeKey}
            </p>

            <small>
                {sense.sourceEventIds.length} source events
                currently attached to this decision case
            </small>
        </article>
    )
}

export function ReasonPanel({
                                dossier,
                                error
                            }: Props) {
    const reason = dossier?.reason

    if (!dossier || !reason) {
        return (
            <article className="stage-card stage-reason stage-waiting">
                <div className="stage-heading">
                    <span>REASON</span>
                    <span className="stage-status">
                        {error ? 'DEGRADED' : 'WAITING'}
                    </span>
                </div>

                <p className="waiting-copy">
                    {error ??
                        'Waiting for a correlated decision case.'}
                </p>
            </article>
        )
    }

    const complete =
        reason.state === 'COMPLETE'

    return (
        <article
            className={
                `stage-card stage-reason ${
                    complete
                        ? 'stage-active'
                        : 'stage-waiting'
                }`
            }
        >
            <div className="stage-heading">
                <span>REASON</span>

                <span className="stage-status">
                    {complete
                        ? `${reason.trustStatus ?? 'UNKNOWN'} TRUST`
                        : words(reason.state)}
                </span>
            </div>

            {complete ? (
                <>
                    <strong className="event-type">
                        {words(reason.recommendation)}
                    </strong>

                    <div className="sense-metrics">
                        <div>
                            <span>Current delay</span>
                            <strong>
                                {fmt(
                                    reason.currentAvgDelay,
                                    'm'
                                )}
                            </strong>
                        </div>

                        <div>
                            <span>Baseline</span>
                            <strong>
                                {fmt(
                                    reason.baselineAvgDelay,
                                    'm'
                                )}
                            </strong>
                        </div>

                        <div>
                            <span>Delta</span>
                            <strong>
                                {fmt(
                                    reason.deltaPct,
                                    '%'
                                )}
                            </strong>
                        </div>

                        <div>
                            <span>Coverage</span>
                            <strong>
                                {fmt(
                                    reason.coveragePct,
                                    '%'
                                )}
                            </strong>
                        </div>
                    </div>

                    <p>
                        {reason.currentSampleSize ?? 0} current trips
                        {' · '}
                        {reason.baselineSampleSize ?? 0} baseline trips
                    </p>

                    <small>
                        {reason.methodologyVersion ?? '—'}
                        {' · '}
                        cutoff{' '}
                        {reason.dataCutoff
                            ? new Date(
                                reason.dataCutoff
                            ).toLocaleTimeString()
                            : '—'}
                        {' · '}
                        future data{' '}
                        {reason.futureDataExcluded
                            ? 'excluded'
                            : 'unknown'}
                    </small>
                </>
            ) : (
                <p className="waiting-copy">
                    Reasoning state: {words(reason.state)}
                </p>
            )}
        </article>
    )
}

export function ActionPanel({
                                dossier,
                                error
                            }: Props) {
    if (!dossier) {
        return (
            <article className="stage-card stage-act stage-waiting">
                <div className="stage-heading">
                    <span>ACT · DECIDE & INTERVENE</span>
                    <span className="stage-status">
                        {error ? 'DEGRADED' : 'WAITING'}
                    </span>
                </div>

                <p className="waiting-copy">
                    Waiting for REASON to produce a case-scoped recommendation.
                </p>

                <small>
                    MoveIQ evaluates whether to execute, request approval,
                    or deliberately take no action.
                </small>
            </article>
        )
    }

    const act = dossier.act
    const reason = dossier.reason

    const noAction =
        act.state === 'NO_ACTION_REQUIRED'

    return (
        <article
            className={
                `stage-card stage-act ${
                    act.state === 'PENDING'
                        ? 'stage-waiting'
                        : 'stage-active'
                }`
            }
        >
            <div className="stage-heading">
                <span>ACT · DECIDE & INTERVENE</span>

                <span
                    className={
                        `stage-status ${
                            noAction
                                ? 'status-detected'
                                : ''
                        }`
                    }
                >
                    {noAction
                        ? 'SKIPPED · MONITOR'
                        : words(act.state)}
                </span>
            </div>

            {noAction ? (
                <>
                    <strong className="event-type">
                        NO INTERVENTION
                    </strong>

                    <p>
                        MoveIQ evaluated this case and deliberately chose
                        not to execute an operational action.
                    </p>

                    <div className="sense-metrics">
                        <div>
                            <span>Recommendation</span>
                            <strong>
                                {words(reason.recommendation)}
                            </strong>
                        </div>

                        <div>
                            <span>Trust</span>
                            <strong>
                                {reason.trustStatus ?? '—'}
                            </strong>
                        </div>

                        <div>
                            <span>Current delay</span>
                            <strong>
                                {fmt(reason.currentAvgDelay, 'm')}
                            </strong>
                        </div>

                        <div>
                            <span>Baseline samples</span>
                            <strong>
                                {reason.baselineSampleSize ?? 0}
                            </strong>
                        </div>
                    </div>

                    <p>
                        <strong>Why:</strong>{' '}
                        evidence does not justify intervention for this case.
                    </p>

                    <small>
                        Policy result: continue observing. A safe action would
                        execute automatically; a higher-risk action would
                        require approval.
                    </small>
                </>
            ) : (
                <>
                    <strong className="event-type">
                        {act.latestEventType
                            ? words(act.latestEventType)
                            : words(act.state)}
                    </strong>

                    <p>
                        MoveIQ selected an intervention from the same
                        decision case.
                    </p>

                    <small>
                        ACT is responsible for policy evaluation,
                        action selection, evidence revalidation,
                        approval when required, and execution.
                    </small>

                    {Object.keys(act.evidence).length > 0 && (
                        <p>
                            {Object.entries(act.evidence)
                                .map(
                                    ([key, value]) =>
                                        `${key}: ${String(value)}`
                                )
                                .join(' · ')}
                        </p>
                    )}
                </>
            )}
        </article>
    )
}

export function VerifyPanel({
                                dossier,
                                error
                            }: Props) {
    if (!dossier) {
        return (
            <article className="stage-card stage-verify stage-waiting">
                <div className="stage-heading">
                    <span>VERIFY · MEASURE OUTCOME</span>

                    <span className="stage-status">
                        {error ? 'DEGRADED' : 'WAITING'}
                    </span>
                </div>

                <p className="waiting-copy">
                    Verification starts only after an intervention executes.
                </p>
            </article>
        )
    }

    const verify = dossier.verify

    const notApplicable =
        verify.state === 'NOT_APPLICABLE'

    return (
        <article
            className={
                `stage-card stage-verify ${
                    verify.state === 'PENDING'
                        ? 'stage-waiting'
                        : 'stage-active'
                }`
            }
        >
            <div className="stage-heading">
                <span>VERIFY · MEASURE OUTCOME</span>

                <span className="stage-status">
                    {notApplicable
                        ? 'NOT STARTED · NO ACTION'
                        : words(verify.state)}
                </span>
            </div>

            {notApplicable ? (
                <>
                    <strong className="event-type">
                        NO OUTCOME TO VERIFY
                    </strong>

                    <p>
                        VERIFY did not run because ACT deliberately executed
                        no intervention for this case.
                    </p>

                    <p>
                        If an action is executed, MoveIQ will compare
                        post-action evidence with the pre-action baseline
                        and classify the observed outcome.
                    </p>

                    <small>
                        Possible result: improvement · no material change ·
                        worsened · insufficient evidence.
                    </small>
                </>
            ) : (
                <>
                    <strong className="event-type">
                        {verify.latestEventType
                            ? words(verify.latestEventType)
                            : words(verify.state)}
                    </strong>

                    <p>
                        MoveIQ is evaluating evidence observed after the
                        intervention against the pre-action state.
                    </p>

                    <small>
                        Historical replay shows observational association;
                        it does not claim causal proof.
                    </small>

                    {Object.keys(verify.evidence).length > 0 && (
                        <p>
                            {Object.entries(verify.evidence)
                                .map(
                                    ([key, value]) =>
                                        `${key}: ${String(value)}`
                                )
                                .join(' · ')}
                        </p>
                    )}
                </>
            )}
        </article>
    )
}