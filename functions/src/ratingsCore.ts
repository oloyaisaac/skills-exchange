// R10-P2-06: ratings that a modified client cannot invent.
//
// Ratings lived in the Room table and nowhere else: they did not aggregate across devices, no operator
// could moderate them, and a tampered client could write its own rows with no server view to
// contradict it. The authorization rules were tested, but they were the *client's* rules.
//
// The server now owns the decision: who may rate whom, once per job per direction, only after
// completion — and the aggregate reputation is derived from those records, not from anything a client
// sends.

export const MIN_STARS = 1;
export const MAX_STARS = 5;
export const MAX_REVIEW_LENGTH = 1000;

/** One rating per job per direction, so a rater cannot stack stars on the same job. */
export function ratingDocumentId(jobId: string, raterUid: string): string {
  if (!jobId || !raterUid) throw new Error("a rating needs a job and a rater");
  return `${jobId}_${raterUid}`;
}

export type RatingCode =
  | "NOT_AUTHENTICATED"
  | "INVALID_STARS"
  | "INVALID_REVIEW"
  | "JOB_NOT_FOUND"
  | "NOT_A_PARTICIPANT"
  | "NOT_COMPLETED"
  | "SELF_RATING"
  | "SUBJECT_MISMATCH"
  | "ALREADY_RATED";

export interface RatingSubject {
  jobId: string;
  /** Job state as stored on the server; only a completed job can be rated. */
  status: string;
  postedByUid?: string;
  assignedProviderUid?: string;
}

export interface RatingRequest {
  raterUid: string;
  subjectUid: string;
  stars: unknown;
  review?: unknown;
}

export interface RatingDecision {
  ok: boolean;
  code?: RatingCode;
  message?: string;
  stars: number;
  review: string;
}

/**
 * Decide whether a rating may be recorded.
 *
 * Ordered deliberately: identity, then shape, then participation, then completion, then self-rating,
 * then the subject relationship, then duplication. Nothing about the request is trusted, including the
 * client's claim about which side it is on: the side is derived from the job document.
 */
export function reviewRating(
  request: RatingRequest,
  job: RatingSubject | null,
  existingRatingId: string | null
): RatingDecision {
  // Only a real number is accepted: `"5"` is a client that is guessing, and guessing is how a
  // tampered client gets a rating recorded it never earned.
  const stars = typeof request.stars === "number" ? request.stars : Number.NaN;
  const review = typeof request.review === "string" ? request.review.trim().slice(0, MAX_REVIEW_LENGTH) : "";

  if (!request.raterUid) {
    return { ok: false, code: "NOT_AUTHENTICATED", message: "Sign in to rate.", stars: 0, review: "" };
  }
  if (!Number.isInteger(stars) || stars < MIN_STARS || stars > MAX_STARS) {
    return {
      ok: false,
      code: "INVALID_STARS",
      message: `A rating is a whole number of stars from ${MIN_STARS} to ${MAX_STARS}.`,
      stars: 0,
      review,
    };
  }
  if (!job) {
    return { ok: false, code: "JOB_NOT_FOUND", message: "That job no longer exists.", stars, review };
  }
  const isResident = job.postedByUid === request.raterUid;
  const isWorker = job.assignedProviderUid === request.raterUid;
  if (!isResident && !isWorker) {
    return {
      ok: false,
      code: "NOT_A_PARTICIPANT",
      message: "Only the resident and the worker on a job can rate each other.",
      stars,
      review,
    };
  }
  if (job.status !== "COMPLETED") {
    return {
      ok: false,
      code: "NOT_COMPLETED",
      message: "You can rate once the job is completed.",
      stars,
      review,
    };
  }
  if (request.subjectUid === request.raterUid) {
    return { ok: false, code: "SELF_RATING", message: "You cannot rate yourself.", stars, review };
  }
  const expectedSubject = isResident ? job.assignedProviderUid : job.postedByUid;
  if (!expectedSubject || request.subjectUid !== expectedSubject) {
    return {
      ok: false,
      code: "SUBJECT_MISMATCH",
      message: "A rating goes to the other party on this job.",
      stars,
      review,
    };
  }
  if (existingRatingId) {
    return {
      ok: false,
      code: "ALREADY_RATED",
      message: "You have already rated this job. Ratings cannot be edited or stacked.",
      stars,
      review,
    };
  }
  return { ok: true, stars, review };
}

export interface RatingAggregate {
  count: number;
  sum: number;
  average: number;
  updatedAt: number;
}

/** Fold one rating into a reputation aggregate. The average is derived, never sent by a client. */
export function applyRating(
  previous: { count?: unknown; sum?: unknown } | undefined,
  stars: number,
  now: number
): RatingAggregate {
  if (!Number.isInteger(stars) || stars < MIN_STARS || stars > MAX_STARS) {
    throw new Error("a stored rating must be an integer from 1 to 5");
  }
  const count = Math.max(0, Math.floor(Number(previous?.count) || 0));
  const sum = Math.max(0, Math.floor(Number(previous?.sum) || 0));
  const nextCount = count + 1;
  const nextSum = sum + stars;
  return {
    count: nextCount,
    sum: nextSum,
    // Two decimals is what the UI shows; one round here keeps every reader consistent.
    average: Math.round((nextSum / nextCount) * 100) / 100,
    updatedAt: now,
  };
}

/** The one number the UI may display for an account, or null when nobody has rated them. */
export function displayRating(aggregate: { count?: unknown; average?: unknown } | undefined): number | null {
  const count = Number(aggregate?.count);
  const average = Number(aggregate?.average);
  if (!Number.isFinite(count) || count <= 0) return null;
  if (!Number.isFinite(average) || average < MIN_STARS || average > MAX_STARS) return null;
  return Math.round(average * 10) / 10;
}
