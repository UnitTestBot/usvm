package org.usvm.samples.psbenchmarks;

public class LoanExam {

    private int calculateCreditInfoPoints(long sum, int deposit, int purpose) {
        int points = 0;

        if (sum <= 1_000_000) {
            points += 12;
        } else if (sum <= 5_000_000) {
            points += 14;
        } else if (sum <= 10_000_000) {
            points += 8;
        } else {
            throw new IllegalArgumentException();
        }

        if (deposit == 0) {
            points += 12;
        } else if (deposit == 1) {
            points += 14;
        } else if (deposit == 2) {
            points += 3;
        } else if (deposit == 3) {
            points += 8;
        } else if (deposit != 4) {
            throw new IllegalArgumentException();
        }

        if (purpose == 0) {
            points += 8;
        } else if (purpose == 1) {
            points += 14;
        } else if (purpose == 2) {
            points += 12;
        } else {
            throw new IllegalArgumentException();
        }

        return points;
    }

    private int calculateAgePoints(long sum, int deposit, int age) {
        if (age >= 21 && age <= 28) {
            if (sum < 1_000_000) {
                return 12;
            }
            if (sum > 3_000_000) {
                return 0;
            }
            return 9;
        }

        if (age >= 29 && age <= 59) {
            return 14;
        }

        if (deposit == 4) {
            return 0;
        }

        if (deposit < 5) {
            return 8;
        }

        throw new IllegalArgumentException();
    }

    private int calculateCertificatePoints(boolean hasCertificate) {
        if (hasCertificate) {
            return 15;
        }

        return 0;
    }

    private int calculateEmploymentPoints(int employment, int age) {
        if (employment == 0) {
            return 14;
        }

        if (employment == 1) {
            return 12;
        }

        if (employment == 2) {
            return 8;
        }

        if (employment == 3) {
            if (age < 70) {
                return 5;
            }

            return 0;
        }

        if (employment == 4) {
            return 0;
        }

        throw new IllegalArgumentException();
    }

    int calculateOtherCreditsPoints(boolean hasOtherCredits, int purpose) {
        if (!hasOtherCredits && purpose != 2) {
            return 15;
        }

        return 0;
    }

    public int getCreditPercent(CreditRequest request) {
        int points = 0;

        points += calculateAgePoints(request.sum, request.deposit, request.age);
        points += calculateCertificatePoints(request.hasCertificate);
        points += calculateEmploymentPoints(request.employment, request.age);
        points += calculateCreditInfoPoints(request.sum, request.deposit, request.creditPurpose);
        points += calculateOtherCreditsPoints(request.hasOtherCredits, request.creditPurpose);

        if (points < 80) {
            return -1;
        }

        if (points < 84) {
            return 30;
        }

        if (points < 88) {
            return 26;
        }

        if (points < 92) {
            return 22;
        }

        if (points < 96) {
            return 19;
        }

        if (points < 100) {
            return 15;
        }

        if (points == 100) {
            return 12;
        }

        throw new IllegalStateException();
    }
}
